package gov.nara.api.poc.services.Imp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.constants.NARAConstants;
import gov.nara.api.poc.pesistance.BOFields;
import gov.nara.api.poc.pesistance.BOFieldsRepo;
import gov.nara.api.poc.pesistance.BOMig;
import gov.nara.api.poc.pesistance.BOMigRepo;
import gov.nara.api.poc.pesistance.BOTasks;
import gov.nara.api.poc.pesistance.BOTasksRepo;
import gov.nara.api.poc.pesistance.BOUserActionHistory;
import gov.nara.api.poc.pesistance.BOUserActionHistoryRepo;
//import gov.nara.api.poc.pesistance.FieldDefs;
//import gov.nara.api.poc.pesistance.FieldDefsRepo;
import gov.nara.api.poc.pesistance.TaskDef;
import gov.nara.api.poc.pesistance.TaskDefRepo;

@Service
public class DataServiceImp {

    @Autowired
    private Environment env;

    @Autowired
    private BOFieldsRepo fieldRepo;

    @Autowired
    private BOTasksRepo taskRepo;

    @Autowired
    private BOUserActionHistoryRepo userActionHistoryRepo;

    @Autowired
    private TaskDefRepo taskDefRepo;

    // @Autowired
    // private FieldDefsRepo fieldDefRepo;

    @Autowired
    private BOMigRepo bOMigRepo;

    private final Logger logger = LoggerFactory.getLogger(DataServiceImp.class);

    public JSONObject getProcessDataFromDB(String boId) {

        JSONObject processInfo = new JSONObject(), allProcessFields = new JSONObject(), Jtemp;
        JSONArray allProcessTasks = new JSONArray(), userActionHistoryList = new JSONArray();

        String action;

        Optional<BOMig> boInfoResult = bOMigRepo.findById(boId);

        if (!boInfoResult.isPresent()) {
            return new JSONObject();
        }
        BOMig boInfo = boInfoResult.get();
        processInfo.put(NARAConstants.TYPE, boInfo.getBoType());
        processInfo.put(NARAConstants.ID, boInfo.getBoId());

        // Task list
        List<BOTasks> taskList = taskRepo.findByBoId(boId);
        Optional<TaskDef> taskKeyResult;
        for (BOTasks task : taskList) {

            Jtemp = new JSONObject();

            taskKeyResult = taskDefRepo.findById(task.getTaskKey());
            if (taskKeyResult.isPresent()) {
                Jtemp.put(NARAConstants.NAME, taskKeyResult.get().getName());
                Jtemp.put(NARAConstants.ACTION, taskKeyResult.get().getAction());
            } else {
                logger.error("This Task (" + task.getTaskKey() + ") is not exist");
                break;
            }

            Jtemp.put(NARAConstants.KEY, task.getTaskKey());
            Jtemp.put(NARAConstants.USERNAME, task.getUserName());

            Jtemp.put(NARAConstants.TO_USER, task.getToUser());
            Jtemp.put(NARAConstants.COMMENTS, task.getComments());

            allProcessTasks.put(Jtemp);
        }

        // Fields List

        List<BOFields> fieldList = fieldRepo.findByBoId(boId);
        for (BOFields field : fieldList) {

            Jtemp = new JSONObject();
            Jtemp.put(NARAConstants.FIELD_VALUE, field.getValue());
            Jtemp.put(NARAConstants.FIELD_KEY, field.getFieldKey());
            Jtemp.put(NARAConstants.FIELD_NAME, NARAConstants.FORM_DATA);
            Jtemp.put(NARAConstants.ID, field.getSeqId());
            action = field.getAction();
            if (!allProcessFields.has(action)) {
                allProcessFields.put(action, new JSONArray());
            }
            allProcessFields.getJSONArray(action).put(Jtemp);

        }

        // user Action History List
        List<BOUserActionHistory> userAHList = userActionHistoryRepo.findByBoId(boId);
        for (BOUserActionHistory userAH : userAHList) {

            Jtemp = new JSONObject();

            Jtemp.put(NARAConstants.NAME, userAH.getName());
            Jtemp.put(NARAConstants.USERNAME, userAH.getUserName());
            Jtemp.put(NARAConstants.USER_ID, userAH.getUserId());
            Jtemp.put(NARAConstants.FIRST_NAME, userAH.getFirstName());
            Jtemp.put(NARAConstants.LAST_NAME, userAH.getLastName());
            Jtemp.put(NARAConstants.TIME_STAMP, userAH.getTimestamp());

            userActionHistoryList.put(Jtemp);

        }

        JSONObject processData = new JSONObject();
        processData.put("processInfo", processInfo);
        processData.put("allProcessTasks", allProcessTasks);
        processData.put("allProcessFields", allProcessFields);
        processData.put("userActionHistoryList", userActionHistoryList);
        processData.put("source", "DB");

        return processData;
    }

    public String loadXmlToDB(JSONObject xmlData) {

        JSONObject processInfo = new JSONObject(), processFieldsOb = new JSONObject(), tempJO;
        JSONArray processTasksList = new JSONArray(), userActionHistoryList = new JSONArray(), tempJA;

        processInfo = xmlData.getJSONObject("processInfo");
        processTasksList = xmlData.getJSONArray("allProcessTasks");
        processFieldsOb = xmlData.getJSONObject("allProcessFields");
        userActionHistoryList = xmlData.getJSONArray("userActionHistoryList");

        if (processInfo.isEmpty() || processTasksList.isEmpty() || processFieldsOb.isEmpty()) {
            logger.error("The data in this XML(" + xmlData.getString("xmlFilePath") + ") is not correct or missing");
            return "ERROR The data in this XML(" + xmlData.getString("xmlFilePath") + ") is not correct or missing";
        }

        String boId = processInfo.getString(NARAConstants.ID);

        Optional<BOMig> boR = bOMigRepo.findById(boId);

        if (boR.isPresent()) {
            logger.error("This BO_ID(" + boId + ") is exist in the DB");
            return "ERROR This BO_ID(" + boId + ") is exist in the DB";
        }

        // Info
        BOMig boMig = new BOMig();
        boMig.setBoId(boId);
        boMig.setBoType(processInfo.getString(NARAConstants.TYPE));
        boMig.setBoXmlFile(xmlData.getString("xmlFilePath"));
        boMig.setMigStatus(0);
        Date date = new Date();
        boMig.setCreatedDate(date);
        boMig.setLastModifiedDate(date);
        bOMigRepo.save(boMig);
        logger.debug("The BO_Id(" + boId + ") Info loaded successfully");

        // Tasks
        List<BOTasks> boTasksList = new ArrayList<BOTasks>();
        BOTasks boTasks;
        String taskKey;
        TaskDef taskKeyValue;
        Optional<TaskDef> taskKeyResult;

        for (Object object : processTasksList) {
            tempJO = (JSONObject) object;

            taskKey = tempJO.getString(NARAConstants.KEY);
            taskKeyResult = taskDefRepo.findById(taskKey);

            if (!taskKeyResult.isPresent()) {
                taskKeyValue = new TaskDef();
                taskKeyValue.setKey(taskKey);
                taskKeyValue.setName(tempJO.getString(NARAConstants.NAME));
                taskKeyValue.setAction(tempJO.getString(NARAConstants.ACTION));
                taskDefRepo.save(taskKeyValue);
            }

            boTasks = new BOTasks();
            boTasks.setBoId(boId);
            boTasks.setComments(tempJO.getString(NARAConstants.COMMENTS));
            boTasks.setTaskKey(taskKey);
            boTasks.setToUser(tempJO.getString(NARAConstants.TO_USER));
            boTasks.setUserName(tempJO.getString(NARAConstants.USERNAME));

            boTasksList.add(boTasks);

        }
        if (!boTasksList.isEmpty())
            taskRepo.saveAll(boTasksList);

        logger.debug("The BO_Id(" + boId + ") Tasks loaded successfully");

        // Fields
        List<BOFields> boFieldList = new ArrayList<BOFields>();
        BOFields bOFields;
        String fieldKey;
        // FieldDefs fieldKeyValue;
        // Optional<FieldDefs> fieldKeyResult;

        for (String action : processFieldsOb.keySet()) {

            tempJA = processFieldsOb.getJSONArray(action);
            for (Object obj : tempJA) {
                tempJO = (JSONObject) obj;

                if (tempJO.has(NARAConstants.FIELD_KEY) && !tempJO.getString(NARAConstants.FIELD_KEY).isEmpty())
                    fieldKey = tempJO.getString(NARAConstants.FIELD_KEY);
                else
                    fieldKey = tempJO.getString(NARAConstants.FIELD_NAME);

                // fieldKeyResult = fieldDefRepo.findById(fieldKey);

                // if (!fieldKeyResult.isPresent()) {
                // fieldKeyValue = new FieldDefs();
                // fieldKeyValue.setKey(fieldKey);
                // fieldDefRepo.save(fieldKeyValue);
                // }

                bOFields = new BOFields();

                bOFields.setAction(action);
                bOFields.setBoId(boId);
                bOFields.setFieldKey(fieldKey);
                if (tempJO.has(NARAConstants.ID) && !tempJO.getString(NARAConstants.ID).isEmpty())
                    bOFields.setSeqId(tempJO.getString(NARAConstants.ID));

                bOFields.setValue(tempJO.getString(NARAConstants.FIELD_VALUE));

                boFieldList.add(bOFields);
            }
        }
        if (!boFieldList.isEmpty())
            fieldRepo.saveAll(boFieldList);

        logger.debug("The BO_Id(" + boId + ") Fields loaded successfully");

        // User Action History
        List<BOUserActionHistory> boActionList = new ArrayList<BOUserActionHistory>();
        BOUserActionHistory boActions;

        for (Object object : userActionHistoryList) {
            tempJO = (JSONObject) object;

            boActions = new BOUserActionHistory();
            boActions.setBoId(boId);
            boActions.setFirstName(tempJO.getString(NARAConstants.FIRST_NAME));
            boActions.setLastName(tempJO.getString(NARAConstants.LAST_NAME));
            boActions.setName(tempJO.getString(NARAConstants.NAME));
            boActions.setTimestamp(tempJO.getString(NARAConstants.TIME_STAMP));
            boActions.setUserId(tempJO.getString(NARAConstants.USER_ID));
            boActions.setUserName(tempJO.getString(NARAConstants.USERNAME));

            boActionList.add(boActions);
        }
        if (!boActionList.isEmpty())
            userActionHistoryRepo.saveAll(boActionList);

        logger.debug("The BO_Id(" + boId + ") User Action History loaded successfully");

        return boId;

    }

    // public JSONObject getProcessDataFromXml(String filePath) {

    // JSONObject processInfo = new JSONObject(), allProcessFields = new
    // JSONObject(), Jtemp;
    // JSONArray allProcessTasks = new JSONArray(), userActionHistoryList = new
    // JSONArray();

    // String action, finalTaskName = "";
    // Node processNode, node;
    // NodeList processesList, fieldsList, tasksList, userActionHis;
    // Element processElement, element;

    // try {
    // File fieldXmlFile = new File(filePath);

    // DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    // DocumentBuilder db = dbf.newDocumentBuilder();

    // Document document = db.parse(fieldXmlFile);
    // document.getDocumentElement().normalize();

    // processesList = document.getElementsByTagName("process");

    // for (int i = 0; i < processesList.getLength(); i++) {

    // processNode = processesList.item(i);

    // if (processNode.getNodeType() == Node.ELEMENT_NODE) {

    // processElement = (Element) processNode;

    // processInfo.put(NARAConstants.TYPE,
    // processElement.getAttribute(NARAConstants.TYPE));
    // processInfo.put(NARAConstants.ID,
    // processElement.getAttribute(NARAConstants.ID));
    // processInfo.put("finalTaskName",
    // processElement.getAttribute("finalTaskName"));

    // // Task list

    // tasksList = processElement.getElementsByTagName("task");

    // finalTaskName = processElement.getAttribute("finalTaskName");

    // for (int e = 0; e < tasksList.getLength(); e++) {
    // node = tasksList.item(e);

    // if (node.getNodeType() == Node.ELEMENT_NODE) {

    // element = (Element) node;

    // Jtemp = new JSONObject();

    // Jtemp.put(NARAConstants.NAME, element.getAttribute(NARAConstants.NAME));
    // Jtemp.put(NARAConstants.KEY, element.getAttribute(NARAConstants.KEY));
    // Jtemp.put(NARAConstants.USERNAME,
    // element.getAttribute(NARAConstants.USERNAME));
    // Jtemp.put(NARAConstants.ACTION, element.getAttribute(NARAConstants.ACTION));
    // Jtemp.put(NARAConstants.TO_USER,
    // element.getAttribute(NARAConstants.TO_USER));
    // Jtemp.put(NARAConstants.COMMENTS,
    // element.getAttribute(NARAConstants.COMMENTS));

    // allProcessTasks.put(Jtemp);

    // if (element.getAttribute(NARAConstants.NAME).equals(finalTaskName))
    // break;
    // }

    // } // end task list

    // // Fields List
    // fieldsList = processElement.getElementsByTagName(NARAConstants.FIELD);

    // for (int e = 0; e < fieldsList.getLength(); e++) {
    // node = fieldsList.item(e);

    // if (node.getNodeType() == Node.ELEMENT_NODE) {

    // element = (Element) node;

    // Jtemp = new JSONObject();

    // if (element.hasAttribute(NARAConstants.FIELD_NAME)) {
    // Jtemp.put(NARAConstants.FIELD_NAME,
    // element.getAttribute(NARAConstants.FIELD_NAME));
    // }
    // if (element.hasAttribute(NARAConstants.FIELD_VALUE)) {
    // Jtemp.put(NARAConstants.FIELD_VALUE,
    // element.getAttribute(NARAConstants.FIELD_VALUE));
    // }
    // if (element.hasAttribute(NARAConstants.FIELD_KEY)) {
    // Jtemp.put(NARAConstants.FIELD_KEY,
    // element.getAttribute(NARAConstants.FIELD_KEY));
    // }

    // if (element.hasAttribute(NARAConstants.ID)) {
    // Jtemp.put(NARAConstants.ID, element.getAttribute(NARAConstants.ID));
    // }

    // action = element.getAttribute(NARAConstants.ACTION);

    // if (!allProcessFields.has(action)) {
    // allProcessFields.put(action, new JSONArray());
    // }
    // allProcessFields.getJSONArray(action).put(Jtemp);
    // }
    // } // end fields list

    // // user Action History List

    // userActionHis = processElement.getElementsByTagName(NARAConstants.ACTION);

    // for (int e = 0; e < userActionHis.getLength(); e++) {
    // node = userActionHis.item(e);

    // if (node.getNodeType() == Node.ELEMENT_NODE) {

    // element = (Element) node;

    // Jtemp = new JSONObject();

    // Jtemp.put(NARAConstants.NAME, element.getAttribute(NARAConstants.NAME));
    // Jtemp.put(NARAConstants.USERNAME,
    // element.getAttribute(NARAConstants.USERNAME));
    // Jtemp.put(NARAConstants.USER_ID,
    // element.getAttribute(NARAConstants.USER_ID));
    // Jtemp.put(NARAConstants.FIRST_NAME,
    // element.getAttribute(NARAConstants.FIRST_NAME));
    // Jtemp.put(NARAConstants.LAST_NAME,
    // element.getAttribute(NARAConstants.LAST_NAME));
    // Jtemp.put(NARAConstants.TIME_STAMP,
    // element.getAttribute(NARAConstants.TIME_STAMP));

    // userActionHistoryList.put(Jtemp);

    // }

    // } // user Action History List

    // }
    // } // next process
    // } catch (ParserConfigurationException e) {
    // logger.error(e.getMessage());
    // e.printStackTrace();
    // } catch (SAXException e) {
    // logger.error(e.getMessage());
    // e.printStackTrace();
    // } catch (IOException e) {
    // logger.error(e.getMessage());
    // e.printStackTrace();
    // }

    // JSONObject processData = new JSONObject();
    // processData.put("processInfo", processInfo);
    // processData.put("allProcessFields", allProcessFields);
    // processData.put("allProcessTasks", allProcessTasks);
    // processData.put("userActionHistoryList", userActionHistoryList);
    // processData.put("xmlFilePath", filePath);
    // processData.put("source", "XML");

    // return processData;
    // }

    public List<Map<String, Object>> runReport(String startDate, String endDate) {

        List<Map<String, Object>> finalResult = new ArrayList<Map<String, Object>>();

        JSONArray allDate = new JSONArray();
        JSONObject temp;

        List<BOMig> result = new ArrayList<>();

        try {
            Date sDate, eDate;

            if (!startDate.isEmpty())
                sDate = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.sss").parse(startDate + " 00:00:00.000");
            else
                sDate = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.sss")
                        .parse(java.time.LocalDate.now().toString() + " 00:00:00.000");

            if (!endDate.isEmpty())
                eDate = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.sss").parse(endDate + " 23:59:59.000");
            else
                eDate = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.sss")
                        .parse(java.time.LocalDate.now().toString() + " 23:59:59.000");

            result = bOMigRepo.findByUpdatedDateBetween(sDate, eDate);

        } catch (ParseException e) {
            System.err.println("Error in Report: " + e.getMessage());
        }

        for (BOMig boState : result) {
            temp = new JSONObject();
            temp.put("id", boState.getBoId());
            temp.put("type", boState.getBoType());
            temp.put("xmlFile", boState.getBoXmlFile());
            temp.put("lastAction", boState.getLastAction());
            temp.put("error", boState.getMigError());
            temp.put("status", env.getProperty("nara.mig.status." + String.valueOf(boState.getMigStatus())));
            temp.put("createDate", boState.getCreatedDate());
            temp.put("updateDate", boState.getLastModifiedDate());

            allDate.put(temp);
        }

        finalResult.add((new JSONObject().put("data", allDate)).toMap());

        return finalResult;

    }

    public List<Map<String, Object>> cleanUpDB(String boId) {

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        if (!boId.isEmpty()) {
            taskRepo.deleteByBoId(boId);
            fieldRepo.deleteByBoId(boId);
            userActionHistoryRepo.deleteByBoId(boId);
            bOMigRepo.deleteById(boId);
        } else {
            taskRepo.deleteAll();
            fieldRepo.deleteAll();
            userActionHistoryRepo.deleteAll();
            bOMigRepo.deleteAll();
        }

        return result;

    }

    public List<Map<String, Object>> getBOStatistics() {
        List<Map<String, Object>> finalResult = new ArrayList<Map<String, Object>>();

        List<Object[]> bOMigList = bOMigRepo.getBoCountGroupByStatus();
        JSONObject temp = new JSONObject();
        Object[] ob;

        for (int i = 0; i < bOMigList.size(); i++) {
            ob = bOMigList.get(i);
            temp.put(env.getProperty("nara.mig.status." + ob[0].toString()), ob[1].toString());
        }

        finalResult.add((new JSONObject().put("data", temp)).toMap());

        return finalResult;

    }

}
