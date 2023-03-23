package gov.nara.api.poc.services.Imp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.pesistance.BOFieldsRepo;
import gov.nara.api.poc.pesistance.BOMig;
import gov.nara.api.poc.pesistance.BOMigRepo;
import gov.nara.api.poc.pesistance.BOTasksRepo;
import gov.nara.api.poc.util.ApiUtil;
import gov.nara.api.poc.util.DataBaseUtil;

@Service
public class ValidationServicesImp {

	@Autowired
	private Environment env;

	@Autowired
	private BOMigRepo bOMigRepo;

	@Autowired
	private ApiUtil apiUtil;

	@Autowired
	private BOTasksRepo taskRepo;

	@Autowired
	private BOFieldsRepo fieldsRepo;

	@Autowired
	private DataBaseUtil dBUtil;

	private final Logger logger = LoggerFactory.getLogger(ValidationServicesImp.class);

	public List<Map<String, Object>> validation(String boId) {

		Map<String, String> boIdList = new HashMap<String, String>();// boId,boType
		JSONObject allBoId = new JSONObject(), temp;

		// get boId from BO_MIG table
		if (!boId.isEmpty()) {
			Optional<BOMig> re = bOMigRepo.findById(boId);
			if (re.isPresent()) {
				BOMig result = re.get();
				if (result.getMigStatus() == 4)
					boIdList.put(result.getBoId(), result.getBoType());
				else
					allBoId.put(result.getBoId(), "This BO_ID is not ready for validation");
			}
		} else {
			List<BOMig> resultList = bOMigRepo.findByMigStatus(4);
			for (BOMig boMig : resultList)
				boIdList.put(boMig.getBoId(), boMig.getBoType());
		}

		String errorMsg = "";
		for (String id : boIdList.keySet()) {
			temp = new JSONObject();

			temp.put("taskWithUserAction", validateTasksWithUserActionHistory(id, boIdList.get(id)));

			if (boIdList.get(id).equals("recordSchedule")) {
				temp.put("numberOfItems", validateNumberOfItems(id));
			}

			for (String str : temp.keySet()) {
				if (!temp.getString(str).equals("OK"))
					errorMsg += temp.getString(str) + "\n";
			}

			if (errorMsg.isEmpty()) {
				dBUtil.updateStatus(id, 5, "", env.getProperty("nara.mig.status.validated"));
				allBoId.put(id, env.getProperty("nara.mig.status.validated"));
			} else {
				dBUtil.updateStatus(id, 6, "Failed Validated: " + errorMsg,
						env.getProperty("nara.mig.status.failedValidated"));
				allBoId.put(id, "Failed Validated: " + errorMsg);
			}

		}

		List<Map<String, Object>> tempListForActionResult = new ArrayList<Map<String, Object>>();
		tempListForActionResult.add((new JSONObject().put("result", allBoId)).toMap());

		return tempListForActionResult;
	}

	private String validateTasksWithUserActionHistory(String boId, String boType) {

		List<String> cookie = apiUtil.getCookies("test-a10");
		JSONObject resJO = new JSONObject(), metaData;
		int uAHListSize = -1;
		String finalResults = "";
		CloseableHttpClient client = HttpClients.createDefault();
		try {

			String url = "";
			if (boType.equals("recordSchedule"))
				url = "?type=RECORDS_SCHEDULE&view=UserActionHistory&t="
						+ String.valueOf(System.currentTimeMillis());
			else if (boType.equals("transferRequest"))
				url = "?type=TRANSFER_REQUEST&view=UserActionHistory&t="
						+ String.valueOf(System.currentTimeMillis());

			HttpGet httpGet = new HttpGet(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + boId + url);

			String result = "";

			httpGet.setHeader("Accept", "application/json");

			for (String s : cookie) {
				httpGet.addHeader("Cookie", s);
			}

			CloseableHttpResponse response = client.execute(httpGet);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {
				resJO = new JSONObject(result);
			} else {
				finalResults = "ERROR in validate Tasks with User Action History with code(" + statusCode + "): "
						+ result;
				logger.error(
						"ERROR in validate Tasks with User Action History with code(" + statusCode + "): " + result);
			}

		} catch (IOException ex) {
			finalResults = "ERROR in validate Tasks with User Action History (IOException): " + ex.getMessage();
			logger.error("ERROR in validate Tasks with User Action History (IOException): " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				finalResults = "ERROR in validate Tasks with User Action History (IOException2): " + ex.getMessage();
				logger.error("ERROR in validate Tasks with User Action History (IOException2): " + ex.getMessage());
			}
		}

		if (finalResults.isEmpty() && !resJO.isEmpty() && resJO.has("oifId") && resJO.getString("oifId").equals(boId))
			if (resJO.has("metadata")) {
				metaData = resJO.getJSONObject("metadata");
				if (metaData.has("H11Kao_v1"))
					uAHListSize = metaData.getJSONArray("H11Kao_v1").length();
			}

		if (uAHListSize != -1) {
			if (taskRepo.getNumberTaskByBoId(boId) == uAHListSize)
				finalResults = "OK";
			else
				finalResults = "ERROR The number of tasks is not equal to the number of User Action History";

		}

		if (finalResults.isEmpty())
			finalResults = "ERROR in validate Tasks with User Action History";

		return finalResults;

	}

	private String validateNumberOfItems(String boId) {

		List<String> cookie = apiUtil.getCookies("test-a10");
		String finalResults = "";
		int numberOfItems = -1;
		CloseableHttpClient client = HttpClients.createDefault();
		try {

			HttpGet httpGet = new HttpGet(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/getNumOfItems/" + boId);

			String result = "";

			httpGet.setHeader("Accept", "application/json");

			for (String s : cookie) {
				httpGet.addHeader("Cookie", s);
			}

			CloseableHttpResponse response = client.execute(httpGet);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {
				numberOfItems = Integer.valueOf(result);
				finalResults = result;
			} else {
				finalResults = "ERROR in validate Number Of Items with code(" + statusCode + "): "
						+ result;
				logger.error(
						"ERROR in validate Number Of Items with code(" + statusCode + "): : " + result);
			}

		} catch (IOException ex) {
			finalResults = "ERROR in validate Number Of Items (IOException): " + ex.getMessage();
			logger.error("ERROR in validate Number Of Items (IOException): " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				finalResults = "ERROR in validate Number Of Items (IOException2): " + ex.getMessage();
				logger.error("ERROR in validate Number Of Items (IOException2): " + ex.getMessage());
			}
		}

		if (numberOfItems != -1)
			if (fieldsRepo.getNumberOfItemsByBoId(boId) == numberOfItems)
				finalResults = "OK";
			else
				finalResults = "ERROR The Number of Items is not equal.";

		if (finalResults.isEmpty())
			finalResults = "ERROR in validate Number Of Items";

		return finalResults;

	}

}
