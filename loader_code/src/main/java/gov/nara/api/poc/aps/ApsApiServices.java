package gov.nara.api.poc.aps;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * TODO Put here a description of what this class does.
 *
 */
@Component
public class ApsApiServices {

	@Autowired
	private Environment env;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * TODO Put here a description of what this method does.
	 *
	 * @param pName
	 * @return
	 */
	public String getProcessInstanceIdByProcessName(String pName) {

		String proInstId = "", result;
		JSONObject filterRequest = new JSONObject(), parameters = new JSONObject(), tempResult, tempJO;
		JSONArray array;
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(this.env.getProperty("nara.aps.serverURL")
				+ this.env.getProperty("nara.aps.activitiURL") + "enterprise/process-instances/filter");
		httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

		filterRequest.put("name", pName);

		parameters.put("filter", filterRequest);

		StringEntity entity;
		try {
			entity = new StringEntity(parameters.toString());

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

					for (Object object : array) {
						tempJO = (JSONObject) object;
						proInstId = tempJO.getString("id");
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

	/**
	 * TODO Put here a description of what this method does.
	 *
	 * @param pInstId
	 * @return
	 */
	public String getActiveTaskIdByProcessInstanceId(String pInstId) {

		String taskId = "", result;
		JSONObject parameters = new JSONObject(), tempResult, tempJO;
		JSONArray array;
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(this.env.getProperty("nara.aps.serverURL")
				+ this.env.getProperty("nara.aps.activitiURL") + "enterprise/tasks/query");
		httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

		parameters.put("processInstanceId", pInstId);

		StringEntity entity;
		try {
			entity = new StringEntity(parameters.toString());

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

					for (Object object : array) {
						tempJO = (JSONObject) object;
						taskId = tempJO.getString("id");
					}

				}
			} else {
				taskId = "ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + ") with code (" + statusCode
						+ "): " + result;
				this.logger.error("ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + ") with code ("
						+ statusCode + "): " + result);
			}

		} catch (UnsupportedEncodingException ex) {
			taskId = "ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + "): " + ex.getMessage();
			this.logger.error("ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + "): " + ex.getMessage());
		} catch (IOException ex) {
			taskId = "ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + "): " + ex.getMessage();
			this.logger.error("ERROR in getActiveTaskIdByProcessInstanceId (" + pInstId + "): " + ex.getMessage());
		}

		return taskId;

	}

	private String getEncryptedBasicAuthorizationCreds() {
		String creds = "";
		creds = env.getProperty("nara.aps.userName") + ":" + env.getProperty("nara.aps.password");
		Base64 base64 = new Base64();
		creds = new String(base64.encode(creds.getBytes()));
		return creds;
	}
}
