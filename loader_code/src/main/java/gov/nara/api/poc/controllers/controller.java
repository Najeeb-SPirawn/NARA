
package gov.nara.api.poc.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jms.Queue;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import gov.nara.api.poc.pesistance.BOMig;
import gov.nara.api.poc.pesistance.BOMigRepo;
import gov.nara.api.poc.services.Imp.DataServiceImp;
import gov.nara.api.poc.services.Imp.PaginationServiceImpl;
import gov.nara.api.poc.services.Imp.ValidationServicesImp;
import gov.nara.api.poc.util.ApiUtil;
import gov.nara.api.poc.util.PaginationUtil;

@RestController
public class controller {

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	private Queue migQueue;

	@Autowired
	private Queue errorQueue;

	@Autowired
	private DataServiceImp dataService;

	@Autowired
	private BOMigRepo bOMigRepo;

	@Autowired
	private ApiUtil apiUtil;

	@Autowired
	ValidationServicesImp validationServices;

	@Autowired
	private PaginationUtil paginationUtil;

	@Autowired
	private PaginationServiceImpl paginationServiceImpl;

	private final Logger logger = LoggerFactory.getLogger(controller.class);

	// @RequestMapping(value = "/naraapi/createBOFromXml", method =
	// RequestMethod.POST)
	// public ResponseEntity<?> loadFromXml(@RequestBody Map<String, Object> body) {

	// JSONObject bodyJO = new JSONObject(body);
	// JSONObject result = new JSONObject();

	// apiUtil.clearCookies();

	// if (bodyJO.has("filePath") && !bodyJO.getString("filePath").isEmpty()) {
	// if (bodyJO.getString("filePath").toLowerCase().endsWith(".xml")) {
	// result.put(bodyJO.getString("filePath"), "Migration started");
	// jmsMessagingTemplate.convertAndSend(migQueue,
	// dataService.getProcessDataFromXml(bodyJO.getString("filePath")).toString());
	// } else
	// result.put(bodyJO.getString("filePath"), "This file is not supported");
	// }

	// if (bodyJO.has("folderPath") && !bodyJO.getString("folderPath").isEmpty())
	// for (final File fileEntry : new
	// File(bodyJO.getString("folderPath")).listFiles()) {
	// if (fileEntry.getAbsolutePath().toLowerCase().endsWith(".xml")) {
	// result.put(fileEntry.getAbsolutePath(), "Migration started");
	// jmsMessagingTemplate.convertAndSend(migQueue,
	// dataService.getProcessDataFromXml(fileEntry.getAbsolutePath()).toString());
	// } else
	// result.put(fileEntry.getAbsolutePath(), "This file is not supported");
	// }

	// List<Map<String, Object>> tempListForActionResult = new ArrayList<Map<String,
	// Object>>();
	// tempListForActionResult.add((new JSONObject().put("data", result)).toMap());

	// return new ResponseEntity(tempListForActionResult, HttpStatus.OK);

	// }

	// @RequestMapping(value = "/naraapi/loadXmlToDB", method = RequestMethod.POST)
	// public ResponseEntity<?> loadXmlIntoDB(@RequestBody Map<String, Object> body)
	// {

	// JSONObject bodyJO = new JSONObject(body);

	// apiUtil.clearCookies();
	// String boId;
	// List<String> boIdList = new ArrayList<>();

	// JSONObject result = new JSONObject();

	// if (bodyJO.has("filePath") && !bodyJO.getString("filePath").isEmpty()) {
	// if (bodyJO.getString("filePath").toLowerCase().endsWith(".xml")) {
	// boId =
	// dataService.loadXmlToDB(dataService.getProcessDataFromXml(bodyJO.getString("filePath")));
	// if (boId.startsWith("ERROR"))
	// result.put(bodyJO.getString("filePath"), boId);
	// else {
	// result.put(bodyJO.getString("filePath"),
	// "The XML loaded successfully to DB with BO_ID (" + boId + ")");
	// boIdList.add(boId);
	// }
	// } else
	// result.put(bodyJO.getString("filePath"), "This file is not supported");
	// }

	// if (bodyJO.has("folderPath") && !bodyJO.getString("folderPath").isEmpty()) {

	// for (final File fileEntry : new
	// File(bodyJO.getString("folderPath")).listFiles()) {
	// if (fileEntry.getAbsolutePath().toLowerCase().endsWith(".xml")) {
	// boId =
	// dataService.loadXmlToDB(dataService.getProcessDataFromXml(fileEntry.getAbsolutePath()));
	// if (boId.startsWith("ERROR"))
	// result.put(fileEntry.getAbsolutePath(), boId);
	// else {
	// result.put(fileEntry.getAbsolutePath(),
	// "The XML loaded successfully to DB with BO_ID (" + boId + ")");
	// boIdList.add(boId);
	// }
	// } else
	// result.put(fileEntry.getAbsolutePath(), "This file is not supported");

	// }

	// }

	// if (bodyJO.has("createBO") && bodyJO.getBoolean("createBO"))
	// for (String id : boIdList) {
	// result.put(id, "Migration started");
	// jmsMessagingTemplate.convertAndSend(migQueue,
	// dataService.getProcessDataFromDB(id).toString());
	// }

	// logger.info(result.toString());
	// List<Map<String, Object>> finalResult = new ArrayList<Map<String, Object>>();
	// finalResult.add((new JSONObject().put("data", result)).toMap());

	// return new ResponseEntity(finalResult, HttpStatus.OK);

	// }

	@RequestMapping(value = "/naraapi/createBOFromDB", method = RequestMethod.POST)
	public ResponseEntity<?> loadFromDB(@RequestBody Map<String, Object> body) {

		Integer pageSize = 0;
		Integer pageNo = 0;

		JSONObject bodyJO = new JSONObject(body), boData, result = new JSONObject();

		if (bodyJO.has("pagesize") && !bodyJO.getString("pagesize").isEmpty()) {
			try {
				pageSize = bodyJO.getInt("pagesize");
			} catch (JSONException e) {
				logger.error(e.getMessage());
			}

		}

		if (bodyJO.has("pageno") && !bodyJO.getString("pageno").isEmpty()) {
			try {
				pageNo = bodyJO.getInt("pageno");
			} catch (JSONException e) {
				logger.error(e.getMessage());
			}
		}

		apiUtil.clearCookies();

		if (bodyJO.has("reMigErrBO") && bodyJO.getBoolean("reMigErrBO")) {

			if (bodyJO.has("boId") && !bodyJO.getString("boId").isEmpty()) {

				result.put(bodyJO.getString("boId"), "Re-Migration started");
				jmsMessagingTemplate.convertAndSend(errorQueue, bodyJO.getString("boId"));

			} else {

				if (bodyJO.has("skip") && !bodyJO.getBoolean("skip")) {
					paginationUtil.setSkip(true);
				} else {
					paginationUtil.setSkip(false);
				}

				if (paginationUtil.isSkip()) {
					List<BOMig> boList = null;
					if (pageSize != 0) {
						boList = bOMigRepo.findByMigStatus(-1, PageRequest.of(pageNo, pageSize));
					} else {
						boList = bOMigRepo.findByMigStatus(-1);
					}

					for (BOMig boMig : boList) {

						result.put(boMig.getBoId(), "Re-Migration started");

						jmsMessagingTemplate.convertAndSend(errorQueue, boMig.getBoId());
					}
				} else {

					result.put("status", "Re-Migration started");
					paginationUtil.setPageSize(pageSize);
					paginationUtil.setPageIndex(pageNo);
					paginationUtil.setStatus(-1);
					paginationServiceImpl.getNextBOPageBystatus();
				}
			}

		} else {

			if (bodyJO.has("boId") && !bodyJO.getString("boId").isEmpty()) {
				boData = dataService.getProcessDataFromDB(bodyJO.getString("boId"));
				if (boData.isEmpty()) {
					logger.info("No data for this BO_Id in the Database");
					result.put(bodyJO.getString("boId"), "No data for this BO_Id in the Database");
				} else {
					result.put(bodyJO.getString("boId"), "Migration started");
					jmsMessagingTemplate.convertAndSend(migQueue, boData.toString());
				}
			} else {

				if (bodyJO.has("skip") && bodyJO.getBoolean("skip")) {
					paginationUtil.setSkip(true);
				} else {
					paginationUtil.setSkip(false);
				}

				if (paginationUtil.isSkip()) {
					List<BOMig> boList = null;
					if (pageSize != 0) {
						boList = bOMigRepo.findByMigStatus(0, PageRequest.of(pageNo, pageSize));
					} else {
						boList = bOMigRepo.findByMigStatus(0);
					}

					for (BOMig boMig : boList) {
						result.put(boMig.getBoId(), "Migration started");

						jmsMessagingTemplate.convertAndSend(migQueue,
								dataService.getProcessDataFromDB(boMig.getBoId()).toString());
					}
				} else {
					result.put("status", "Migration started");
					paginationUtil.setPageSize(pageSize);
					paginationUtil.setPageIndex(pageNo);
					paginationUtil.setStatus(0);
					paginationServiceImpl.getNextBOPageBystatus();
				}
			}

		}

		List<Map<String, Object>> tempListForActionResult = new ArrayList<Map<String, Object>>();
		tempListForActionResult.add((new JSONObject().put("data", result)).toMap());

		return new ResponseEntity(tempListForActionResult, HttpStatus.OK);

	}

	@RequestMapping(value = "/naraapi/reporting", method = RequestMethod.POST)
	public ResponseEntity<?> runReport(@RequestBody Map<String, Object> body) {

		JSONObject bodyJO = new JSONObject(body);

		String startDate = "", endDate = "";

		if (bodyJO.has("startDate"))
			startDate = bodyJO.getString("startDate");

		if (bodyJO.has("endDate"))
			endDate = bodyJO.getString("endDate");

		return new ResponseEntity(dataService.runReport(startDate, endDate), HttpStatus.OK);

	}

	@RequestMapping(value = "/naraapi/cleanUpDB", method = RequestMethod.POST)
	public ResponseEntity<?> cleanUpDB(@RequestBody Map<String, Object> body) {
		apiUtil.clearCookies();

		JSONObject bodyJO = new JSONObject(body);

		String boId = "";

		if (bodyJO.has("boId"))
			boId = bodyJO.getString("boId");

		return new ResponseEntity(dataService.cleanUpDB(boId), HttpStatus.OK);

	}

	@RequestMapping(value = "/naraapi/validateBO", method = RequestMethod.POST)
	public ResponseEntity<?> validation(@RequestBody Map<String, Object> body) {
		apiUtil.clearCookies();

		JSONObject bodyJO = new JSONObject(body);

		String boId = "";

		if (bodyJO.has("boId"))
			boId = bodyJO.getString("boId");

		return new ResponseEntity(validationServices.validation(boId), HttpStatus.OK);

	}

	@RequestMapping(value = "/naraapi/getBOStatistics", method = RequestMethod.GET)
	public ResponseEntity<?> getBOStatistics() {
		apiUtil.clearCookies();

		return new ResponseEntity(dataService.getBOStatistics(), HttpStatus.OK);

	}

}