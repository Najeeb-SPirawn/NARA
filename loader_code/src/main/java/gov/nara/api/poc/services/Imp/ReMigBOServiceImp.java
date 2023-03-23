package gov.nara.api.poc.services.Imp;

import java.io.IOException;
import java.util.List;

import javax.jms.Queue;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.constants.NARAConstants;
import gov.nara.api.poc.pesistance.BOTasksRepo;
import gov.nara.api.poc.util.ApiUtil;
import gov.nara.api.poc.util.DataBaseUtil;
import gov.nara.api.poc.util.PaginationUtil;

@Service
public class ReMigBOServiceImp {

	@Autowired
	private Environment env;

	@Autowired
	private DataBaseUtil dBUtil;

	@Autowired
	private ApiUtil apiUtil;

	@Autowired
	private Queue migQueue;

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	private DataServiceImp dataService;
	@Autowired
	private BOTasksRepo boTaskRepo;

	@Autowired
	private PaginationUtil paginationUtil;

	@Autowired
	private PaginationServiceImpl paginationServiceImpl;

	private final Logger logger = LoggerFactory.getLogger(ReMigBOServiceImp.class);

	@JmsListener(destination = "error.queue")
	public void receiveMessage(String boId) {

		String userForDeleteBO = env.getProperty("nara.era2.userName.deleteBO");

		if (userForDeleteBO == null || userForDeleteBO.isEmpty())
			userForDeleteBO = boTaskRepo.getUserNameByBOandTask(boId, "CREATE");

		if (userForDeleteBO == null || userForDeleteBO.isEmpty())
			logger.error("Return from deleteBO no delete user for this BO: " + boId);

		List<String> cookie = apiUtil.getCookies(userForDeleteBO);

		String resultFromApi = deleteBO(boId, cookie);

		if (resultFromApi.isEmpty() || resultFromApi.startsWith(NARAConstants.ERROR)) {

			logger.error("Return from deleteBO (" + boId + ") : " + resultFromApi);

		} else {

			dBUtil.updateStatus(boId, 0, "", "deleteBO");

			logger.info("The Status for BO(" + boId + ") has been changed successfully");

			jmsMessagingTemplate.convertAndSend(migQueue, dataService.getProcessDataFromDB(boId).toString());
		}

		if (!paginationUtil.isSkip()) {

			if (paginationUtil.decreaseBatchSize()) {
				if (paginationUtil.getTotalBOsCount().equals(0)) {
					logger.info("Migration done");
					paginationUtil.setSkip(true);
				} else {
					paginationServiceImpl.getNextBOPageBystatus();
				}
			}
		}

	}

	private String deleteBO(String boId, List<String> cookie) {

		String finalResults = "";

		CloseableHttpClient client = HttpClients.createDefault();
		try {

			HttpDelete httpDelete = new HttpDelete(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + boId);

			String result = "";
			httpDelete.setHeader("Accept", "application/json");

			for (String s : cookie) {
				httpDelete.addHeader("Cookie", s);
			}

			httpDelete.addHeader("t", String.valueOf(System.currentTimeMillis()));

			CloseableHttpResponse response = client.execute(httpDelete);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {

				finalResults = "The BO(" + boId + ") has been deleted successfully";
				logger.info("The BO(" + boId + ") has been deleted successfully");

			} else {
				finalResults = "ERROR in deleteBO with code (" + statusCode + "): " + result;
				logger.error("ERROR in deleteBO with code (" + statusCode + "): " + result);
			}

		} catch (IOException ex) {
			finalResults = "ERROR in deleteBO: " + ex.getMessage();
			logger.error("ERROR in deleteBO: " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				finalResults = "ERROR in deleteBO: " + ex.getMessage();
				logger.error("ERROR in deleteBO: " + ex.getMessage());
			}
		}

		return finalResults;

	}
}
