package gov.nara.api.poc.services.Imp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.util.ApiUtil;

@Service
public class DeleteAPSServiceImp {
	@Autowired
	private Environment env;

	@Autowired
	private ApiUtil apiUtil;

	private final Logger logger = LoggerFactory.getLogger(DeleteAPSServiceImp.class);

	public ResponseEntity<?> deleteAllProcess() {
		JSONObject reqBody = new JSONObject();
		reqBody.put("finished", false);

		JSONArray deletedList = new JSONArray();

		JSONArray dataArr = getAlldataFromAPI(env.getProperty("aps.url"),
				"/enterprise/historic-process-instances/query", reqBody, "start");

		for (Object object : dataArr) {
			JSONObject appJO = (JSONObject) object;
			String id = appJO.getString("id");
			deleteProcessById(id);
			deleteProcessById(id);
			deletedList.put(id);
		}

		reqBody = new JSONObject();
		reqBody.put("finished", true);

		dataArr = getAlldataFromAPI(env.getProperty("aps.url"),
				"/enterprise/historic-process-instances/query", reqBody, "start");

		for (Object object : dataArr) {
			JSONObject appJO = (JSONObject) object;
			String id = appJO.getString("id");
			System.out.println(id);
			deleteProcessById(id);
			deletedList.put(id);
		}

		return ResponseEntity
				.ok((new JSONArray().put(new JSONObject().put("Processes deleted", deletedList))).toString());

	}

	JSONArray getAlldataFromAPI(String url, String endPoint, JSONObject reqBody, String pageORstart) {
		JSONArray dataArr = new JSONArray();

		String resultFromAPI;
		JSONObject resultFromAPIJO, appJO;

		boolean hasMore = true;

		while (hasMore) {

			resultFromAPI = httpPost(url, endPoint, reqBody);

			if (resultFromAPI != null && !resultFromAPI.startsWith("ERROR")) {
				resultFromAPIJO = new JSONObject(resultFromAPI);
				if (resultFromAPIJO != null && !resultFromAPIJO.isEmpty()) {
					if (resultFromAPIJO.has("data")) {
						for (Object object : resultFromAPIJO.getJSONArray("data")) {
							appJO = (JSONObject) object;
							dataArr.put(appJO);
						}
					}
					resultFromAPIJO.remove("data");
					int nextPage = getNextPage(resultFromAPIJO);
					if (nextPage == 0)
						hasMore = false;
					else
						reqBody.put(pageORstart, nextPage);
				}
			} else
				hasMore = false;
		} // end while loop

		return dataArr;
	}

	void deleteProcessById(String id) {

		CloseableHttpClient client = HttpClients.createDefault();
		try {

			HttpDelete httpDelete = new HttpDelete(env.getProperty("aps.url") +
					"/enterprise/process-instances/" + id);
			httpDelete.setHeader("Accept", "*/*");
			httpDelete.setHeader("Authorization",
					"Basic " + getEncryptedBasicAuthorizationCreds());

			HttpResponse response = client.execute(httpDelete);
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200)
				this.logger.info("Process ID (" + id + ") was successfully deleted.");
			else
				this.logger.debug("Warning: Failed to delete process ID (" + id + ") code ("
						+ statusCode + ")");

		} catch (IOException ex) {
			this.logger.error("ERROR in httpDelete: " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				this.logger.error("ERROR in httpDelete: " + ex.getMessage());
			}
		}

	}

	String httpPost(String url, String endPoint, JSONObject bodyJO) {

		String result = null;

		CloseableHttpClient client = HttpClients.createDefault();
		try {

			HttpPost httpPost = new HttpPost(url + endPoint);

			if (!bodyJO.isEmpty()) {
				StringEntity entity = new StringEntity(bodyJO.toString());
				httpPost.setEntity(entity);
				httpPost.setHeader("Content-type",
						"application/json");
			}

			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Authorization",
					"Basic " + getEncryptedBasicAuthorizationCreds());

			CloseableHttpResponse response = client.execute(httpPost);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode != 200) {
				this.logger.error("ERROR in httpPost with code (" + statusCode + "): " + result);
				result = "ERROR in http POST with code (" + statusCode + "): " + result;
			}

		} catch (IOException ex) {

			this.logger.error("ERROR in httpPost: " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				this.logger.error("ERROR in httpPost: " + ex.getMessage());
			}
		}
		return result;
	}

	int getNextPage(JSONObject result) {

		if (!result.isEmpty()) {
			if (result.has("size") && result.has("total")) {
				int size = result.getInt("size"),
						total = result.getInt("total"),
						start = result.getInt("start");
				if ((start + size) < total) {
					return (start / size) + 1;
				}
			}
		}
		return 0;

	}

	String getEncryptedBasicAuthorizationCreds() {

		String creds = env.getProperty("aps.userName") + ":"
				+ env.getProperty("aps.password");
		Base64 base64 = new Base64();
		creds = new String(base64.encode(creds.getBytes()));
		return creds;
	}

	public ResponseEntity<?> delete_bo_from_aps_by_id(Map<String, Object> body) {

		JSONArray result = new JSONArray(), tempDeletedList;
		JSONObject tempDeletedBO;

		String boidsString = (String) body.get("boIds");
		String userId = (String) body.get("userId");
		if (boidsString.isEmpty())
			return ResponseEntity
					.ok((new JSONArray().put(new JSONObject().put("ERROR", "BO Id is empty !!! "))).toString());

		if (userId.isEmpty())
			return ResponseEntity
					.ok((new JSONArray().put(new JSONObject().put("ERROR", "Username is empty !!! "))).toString());

		String[] boidsArray = boidsString.split(",");
		String proInstIds = "", resultFromMethod = "";
		for (String boid : boidsArray) {
			tempDeletedBO = new JSONObject();
			tempDeletedBO.put("BO_Id", boid);
			proInstIds = getProcessInstanceIdByProcessName(boid);

			if (proInstIds.isEmpty() || proInstIds.startsWith("ERROR")) {

				if (proInstIds.startsWith("ERROR"))
					tempDeletedBO.put("ERROR", proInstIds);
				else
					tempDeletedBO.put("ERROR", "This BO Id is not found in APS !!!");

				result.put(tempDeletedBO);
				continue;
			}

			if (boid.startsWith("DAA") || boid.startsWith("DAL")) {

				resultFromMethod = getItemsByBoIdFromNaraApi(boid, userId);

				if (resultFromMethod.startsWith("ERROR")) {
					logger.error("Return from getItemsByBoIdFromNaraApi (" + boid + "): "
							+ resultFromMethod);
					tempDeletedBO.put("ERROR", "Get Items From Nara Api: " + resultFromMethod);
					result.put(tempDeletedBO);
					continue;
				}

				Map<String, String> itemUuidFromApi = getItemUuid(resultFromMethod);

				if (itemUuidFromApi == null) {
					logger.error("Return from getItemUuid (" + boid + "): " + resultFromMethod);
					tempDeletedBO.put("ERROR", "UUID is NULL");

				}

				if (itemUuidFromApi.isEmpty()) {
					logger.error("Return from getItemUuid (" + boid + ") no Items for deleting");
				}

				String itemProInstId;
				tempDeletedList = new JSONArray();
				for (String itemId : itemUuidFromApi.keySet()) {
					itemProInstId = getProcessInstanceIdByProcessName(itemUuidFromApi.get(itemId));
					deleteProcessById(itemProInstId);
					deleteProcessById(itemProInstId);
					tempDeletedList.put(itemId);
				}

				if (tempDeletedList.isEmpty())
					tempDeletedBO.put("Items", "No Items for deleting");
				else
					tempDeletedBO.put("Items", tempDeletedList);

			}

			tempDeletedList = new JSONArray();
			for (String id : proInstIds.split(",")) {
				deleteProcessById(id);
				deleteProcessById(id);
				tempDeletedList.put(id);
			}

			if (tempDeletedList.isEmpty())
				tempDeletedBO.put("ProcessInstId", "No process for deleting");
			else
				tempDeletedBO.put("ProcessInstId", tempDeletedList);

			result.put(tempDeletedBO);

		}

		return ResponseEntity
				.ok((new JSONArray().put(new JSONObject().put("Result", result))).toString());

	}

	private String getItemsByBoIdFromNaraApi(String oifId, String naraUser) {

		String finalResults = "", result = "";

		List<String> cookie = apiUtil.getCookies(naraUser);

		if (cookie.isEmpty()) {

			logger.error(
					"ERROR: Login for getItemsByBoIdFromNaraApi for the BO: (" + oifId + ") is failed for this user: "
							+ naraUser);

			return "ERROR: Login for getItemsByBoIdFromNaraApi item for the BO: (" + oifId
					+ ") is failed for this user: "
					+ naraUser;
		}
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet httpGet;
		CloseableHttpResponse response;
		int statusCode;

		try {

			httpGet = new HttpGet(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId
					+ "/item-group-tree/false");

			httpGet.setHeader("Accept", "application/json");

			for (String s : cookie) {
				httpGet.addHeader("Cookie", s);
			}

			httpGet.addHeader("t", String.valueOf(System.currentTimeMillis()));

			response = client.execute(httpGet);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {

				finalResults = result;

			} else {
				finalResults = "ERROR in getItemsByBoIdFromNaraApi for BO (" + oifId
						+ ")  with code(" + statusCode + "): " + result;
				logger.error("ERROR in getItemsByBoIdFromNaraApi for BO (" + oifId
						+ ")  with code(" + statusCode + "): " + result);
			}

		} catch (IOException ex) {
			finalResults = "ERROR in getItemsByBoIdFromNaraApi BO (" + oifId + "):" + ex.getMessage();
			logger.error("ERROR in getItemsByBoIdFromNaraApi BO (" + oifId + "):" + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				finalResults = "ERROR in getItemsByBoIdFromNaraApi BO (" + oifId + "):" + ex.getMessage();
				logger.error("ERROR in getItemsByBoIdFromNaraApi BO (" + oifId + "):" + ex.getMessage());
			}
		}
		return finalResults;

	}

	private Map<String, String> getItemUuid(String resultFromMethod) {

		Map<String, String> BoIDs = new HashMap<>();

		try {
			JSONObject jsonObject = new JSONObject(resultFromMethod);

			for (String key : jsonObject.keySet()) {
				JSONObject keyValueJsonObject = jsonObject.getJSONObject(key);
				String type = keyValueJsonObject.getString("type");
				String id = keyValueJsonObject.getString("id");
				String humanReadableId = keyValueJsonObject.getString("humanReadableId");

				if (type.equals("Items"))
					BoIDs.put(humanReadableId + " >>> " + id, id);

				if (keyValueJsonObject.has("children")) {
					JSONArray children = keyValueJsonObject.getJSONArray("children");

					if (children != null)
						for (int i = 0; i < children.length(); i++)
							processChild(children.getJSONObject(i), BoIDs);

				}

			}

		} catch (JSONException e) {
			logger.error("ERROR in getItemUuid: " + e.getMessage());
			return null;
		}

		return BoIDs;
	}

	private void processChild(JSONObject child, Map<String, String> BoIDs) {

		String childType = child.getString("type");
		String childId = child.getString("id");
		String childHumanReadableId = child.getString("humanReadableId");

		if (childType.equals("Items"))
			BoIDs.put(childHumanReadableId + " >>> " + childId, childId);

		if (child.has("children")) {
			JSONArray grandChildren = child.getJSONArray("children");
			if (grandChildren != null)
				for (int i = 0; i < grandChildren.length(); i++)
					processChild(grandChildren.getJSONObject(i), BoIDs);
		}

	}

	public String getProcessInstanceIdByProcessName(String pName) {

		List<String> proInstIdList = new ArrayList<String>();
		String proInstId = "";
		String result;
		JSONObject filterRequest = new JSONObject(), parameters = new JSONObject(), tempResult;
		JSONArray array;
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(env.getProperty("aps.url") + "/enterprise/process-instances/filter");
		httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

		filterRequest.put("name", pName);

		parameters.put("filter", filterRequest);

		StringEntity entity;
		try {
			entity = new StringEntity(parameters.toString(), "UTF-8");

			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			CloseableHttpResponse response = client.execute(httpPost);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {
				tempResult = new JSONObject(result);
				if (tempResult.has("data")) {
					array = tempResult.getJSONArray("data");

					if (array.length() == 1)
						proInstId = ((JSONObject) array.get(0)).getString("id");
					else {
						for (Object object : array)
							proInstIdList.add(((JSONObject) object).getString("id"));

						proInstId = proInstIdList.toString().replace("[", "").replace("]", "").replace(" ", "");
					}
				}
			} else {
				proInstId = "ERROR in getProcessInstanceIdByProcessName (" + pName + ") with code (" + statusCode
						+ "): " + result;
				this.logger.error("ERROR in getProcessInstanceIdByProcessName (" + pName + ") with code (" + statusCode
						+ "): " + result);
			}

		} catch (UnsupportedEncodingException ex) {
			proInstId = "ERROR in getProcessInstanceIdByProcessName (" + pName + "): " + ex.getMessage();
			this.logger.error("ERROR in getProcessInstanceIdByProcessName (" + pName + "): " + ex.getMessage());
		} catch (IOException ex) {
			proInstId = "ERROR in getProcessInstanceIdByProcessName (" + pName + "): " + ex.getMessage();
			this.logger.error("ERROR in getProcessInstanceIdByProcessName (" + pName + "): " + ex.getMessage());
		}

		return proInstId;

	}
}