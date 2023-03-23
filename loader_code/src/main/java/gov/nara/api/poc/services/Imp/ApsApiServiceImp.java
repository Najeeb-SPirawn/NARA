package gov.nara.api.poc.services.Imp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
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
import org.springframework.stereotype.Service;

import org.apache.commons.codec.binary.Base64;

@Service
public class ApsApiServiceImp {

    @Autowired
    private Environment env;

    private final Logger logger = LoggerFactory.getLogger(ApsApiServiceImp.class);

    public String getProcessInstanceIdByProcessName(String pName) {

        String proInstId = "", result;
        JSONObject filterRequest = new JSONObject(), parameters = new JSONObject(), tempResult, tempJO;
        JSONArray array;
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(env.getProperty("nara.aps.serverURL")
                + env.getProperty("nara.aps.activitiURL") + "enterprise/process-instances/filter");
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

    public String getfromDataVarFromTaskFromByTaskId(String taskId) {

        String result;
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(this.env.getProperty("nara.aps.serverURL")
                + this.env.getProperty("nara.aps.activitiURL") + "enterprise/task-forms/" + taskId);

        httpGet.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

        httpGet.setHeader("Accept", "application/json");

        try {

            CloseableHttpResponse response = client.execute(httpGet);

            result = EntityUtils.toString(response.getEntity(), "UTF-8");
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                JSONObject tempResult = new JSONObject(result), fields;
                JSONArray temArray;

                if (!tempResult.isEmpty() && tempResult.has("fields")) {
                    if (tempResult.getJSONArray("fields").length() > 0) {
                        fields = tempResult.getJSONArray("fields").getJSONObject(0).getJSONObject("fields");
                        if (fields.has("1")) {
                            temArray = fields.getJSONArray("1");
                            for (Object object : temArray) {
                                JSONObject tempJO = (JSONObject) object;
                                if (tempJO.has("id") && tempJO.getString("id").equals("formdata"))
                                    result = tempJO.getString("value");

                            }
                        }
                    }
                }

            } else {
                result = "ERROR in getFormdataVariableDataByTaskId (" + taskId + ") with code (" + statusCode
                        + "): " + result;
                this.logger.error("ERROR in getFormdataVariableDataByTaskId (" + taskId + ") with code ("
                        + statusCode + "): " + result);
            }

        } catch (UnsupportedEncodingException ex) {
            result = "ERROR in getFormdataVariableDataByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in getFormdataVariableDataByTaskId (" + taskId + "): " + ex.getMessage());
        } catch (IOException ex) {
            result = "ERROR in getFormdataVariableDataByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in getFormdataVariableDataByTaskId (" + taskId + "): " + ex.getMessage());
        }

        return result;
    }

    public String updateFormDataVariableByTaskId(String taskId, String formDataValue) {
        String result;
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(this.env.getProperty("nara.aps.serverURL")
                + this.env.getProperty("nara.aps.activitiURL") + "enterprise/tasks/" + taskId + "/variables/formdata");
        JSONObject parameters = new JSONObject();
        parameters.put("value", formDataValue);
        parameters.put("type", "string");
        parameters.put("scope", "global");
        parameters.put("name", "formdata");

        StringEntity entity;

        try {
            entity = new StringEntity(parameters.toString(), "UTF-8");
            httpPut.setEntity(entity);
            httpPut.setHeader("Accept", "application/json");
            httpPut.setHeader("Content-type", "application/json");
            httpPut.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());
            CloseableHttpResponse response = client.execute(httpPut);
            result = EntityUtils.toString(response.getEntity(), "UTF-8");
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                result = "SUCCESS";
            } else {
                result = "ERROR in updateFormDataVariableByTaskId (" + taskId + ") with code (" + statusCode
                        + "): " + result;
                this.logger.error("ERROR in updateFormDataVariableByTaskId (" + taskId + ") with code ("
                        + statusCode + "): " + result);
            }
        } catch (UnsupportedEncodingException ex) {
            result = "ERROR in updateFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in updateFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage());
        } catch (IOException ex) {
            result = "ERROR in updateFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in updateFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage());
        }

        return result;

    }

    public String saveFormDataVariableByTaskId(String taskId) {

        String result;
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(this.env.getProperty("nara.aps.serverURL")
                + this.env.getProperty("nara.aps.activitiURL") + "enterprise/task-forms/" + taskId + "/save-form");

        JSONObject parameters = new JSONObject();
        parameters.put("values", new JSONObject());

        StringEntity entity;

        try {
            entity = new StringEntity(parameters.toString(), "UTF-8");
            httpPost.setEntity(entity);

            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());
            CloseableHttpResponse response = client.execute(httpPost);
            result = EntityUtils.toString(response.getEntity(), "UTF-8");
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                result = "SUCCESS";
            } else {
                result = "ERROR in saveFormDataVariableByTaskId (" + taskId + ") with code (" + statusCode
                        + "): " + result;
                this.logger.error("ERROR in saveFormDataVariableByTaskId (" + taskId + ") with code ("
                        + statusCode + "): " + result);
            }
        } catch (UnsupportedEncodingException ex) {
            result = "ERROR in saveFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in saveFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage());
        } catch (IOException ex) {
            result = "ERROR in saveFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage();
            this.logger.error("ERROR in saveFormDataVariableByTaskId (" + taskId + "): " + ex.getMessage());
        }

        return result;
    }

}
