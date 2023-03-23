package gov.nara.api.poc.services.Imp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
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
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.constants.NARAConstants;
import gov.nara.api.poc.pesistance.BOFieldsRepo;
//import gov.nara.api.poc.pesistance.BOCatsDataRepo;
import gov.nara.api.poc.pesistance.BOItemStatusRepo;
import gov.nara.api.poc.pesistance.BOTasksRepo;
import gov.nara.api.poc.util.ApiUtil;
import gov.nara.api.poc.util.DataBaseUtil;
import gov.nara.api.poc.util.PaginationUtil;

@Service
public class MigBOServiceImp {
	@Autowired
	private Environment env;

	@Autowired
	private DataBaseUtil dBUtil;

	@Autowired
	private ApiUtil apiUtil;

	@Autowired
	private BOTasksRepo boTaskRepo;

	@Autowired
	private BOItemStatusRepo itemStatusRepo;

	@Autowired
	private BOFieldsRepo bOFieldsRepo;

	@Autowired
	private ApsApiServiceImp apsApiServiceImp;

	// @Autowired
	// private BOCatsDataRepo bOCatsDataRepo;

	@Autowired
	private PaginationUtil paginationUtil;

	@Autowired
	private PaginationServiceImpl paginationServiceImpl;

	private final Logger logger = LoggerFactory.getLogger(MigBOServiceImp.class);

	@JmsListener(destination = "mig.queue")
	public void receiveMessage(String boData) {

		JSONObject processInfoOb = new JSONObject(), processFieldsOb = new JSONObject(), taskJO = new JSONObject(),
				tempJO = new JSONObject(), boDataOb = new JSONObject(boData);

		JSONArray processTasksList = new JSONArray(), userActionHistoryList = new JSONArray(),
				arrForLog = new JSONArray(), attArray = new JSONArray(), reviewArr = new JSONArray();

		String oifId = "", itemId = "", action, taskLog = "", resultFromMethod = "", groupId = "", BOId = "",
				BOType = "", boDataSource = "", filePath = "", pSubmitTask = "";

		Map<String, String> itemsUuids = null;

		boolean stopProcess = false;

		List<String> cookie, inactiveItemsIds = new ArrayList<String>();
		int idCount = 0, maxSeqId = 0;

		processInfoOb = boDataOb.getJSONObject("processInfo");
		processTasksList = boDataOb.getJSONArray("allProcessTasks");
		processFieldsOb = boDataOb.getJSONObject("allProcessFields");
		userActionHistoryList = boDataOb.getJSONArray("userActionHistoryList");
		boDataSource = boDataOb.getString("source");

		BOId = processInfoOb.getString(NARAConstants.ID);
		BOType = processInfoOb.getString(NARAConstants.TYPE);

		if (BOId.isEmpty())
			logger.error("BO_ID is empty !!!!!!!!!!!");
		else {
			if (dBUtil.checkBOid(BOId) > 0)
				stopProcess = true;
		}

		if (boDataSource.equals("XML")) {

			filePath = boDataOb.getString("xmlFilePath");
			if (!processTasksList.isEmpty()) {
				logger.info("The process info loaded from XML successfully");
				dBUtil.createStatus(BOId, 1, "", BOType, filePath, "loadXml");
			} else {
				logger.error(" (" + BOId + ") No data in the XML file (" + filePath + ")");
				dBUtil.createStatus(BOId, -1, "No tasks in the XML file", BOType, filePath, "loadXml");
			}

		} else if (boDataSource.equals("DB")) {

			if (!processTasksList.isEmpty()) {
				logger.info("The process info loaded from DB successfully");
				dBUtil.updateStatus(BOId, 1, "", "loadDB");
			} else {
				logger.error("No Tasks for this BO_ID (" + BOId + ")");
				dBUtil.updateStatus(BOId, -1, "No Tasks for this BO_ID(" + BOId + ")", "loadDB");
			}

		}

		for (Object task : processTasksList) {
			if (stopProcess)
				break;

			taskJO = (JSONObject) task;

			cookie = apiUtil.getCookies(taskJO.getString(NARAConstants.USERNAME));

			if (cookie.isEmpty()) {

				logger.error("Login FAIL for user (" + taskJO.getString(NARAConstants.USERNAME) + ") (" + BOId + ")");
				arrForLog.put("Login FAIL for user: " + taskJO.getString(NARAConstants.USERNAME));

				dBUtil.updateStatus(BOId, -1, "Login Fail for user: " + taskJO.getString(NARAConstants.USERNAME),
						"login");
				stopProcess = true;
				break;
			}

			action = taskJO.getString(NARAConstants.ACTION);

			switch (action) {

				case NARAConstants.BO_ACTION_CREATEBO:
					tempJO = createBO(processFieldsOb, BOId, BOType, userActionHistoryList, cookie);

					if (tempJO.isEmpty() || tempJO.has(NARAConstants.ERROR) || !tempJO.has(NARAConstants.OIF_ID)) {
						logger.error("Return from CreateBo (" + BOId + "): " + tempJO.toString());
						dBUtil.updateStatus(BOId, -1, "Return from Create Bo: " + tempJO.toString(), action);
						taskLog = tempJO.toString();
						stopProcess = true;
						break;
					}

					oifId = tempJO.getString(NARAConstants.OIF_ID);

					dBUtil.updateStatus(oifId, 2, "", action);

					resultFromMethod = updateBO(processFieldsOb, BOType, oifId, userActionHistoryList, cookie);

					if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
						logger.error("Return from UpdateBo (" + oifId + "): " + resultFromMethod);
						taskLog = resultFromMethod;
						dBUtil.updateStatus(oifId, -1, "Return from Update Bo: " + resultFromMethod, "updateBO");
						stopProcess = true;
						break;
					}

					if (resultFromMethod.isEmpty()) {
						logger.info("No data for update");
					}

					// Contacts
					idCount = 0;
					maxSeqId = getMaxSeqIdByAction(oifId, "new_contact");

					while (idCount < maxSeqId && maxSeqId != 0) {
						idCount++;
						tempJO = getProcessFields(processFieldsOb, "new_contact", idCount);

						if (!tempJO.isEmpty()) {

							resultFromMethod = addContactToBO(BOType, oifId, tempJO, cookie, boDataSource);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from Add Contact to BO (" + oifId + "): " + resultFromMethod);
								dBUtil.updateStatus(oifId, -1, "Return from Add Contact to BO: " + resultFromMethod,
										"addContact");
								taskLog = resultFromMethod;
								stopProcess = true;
								break;
							}

						} else {
							logger.info("No more new contacts for adding");
							// break;
						}

					}
					System.out.println("new_contact: " + maxSeqId + " >>> idCount:" + idCount);
					if (stopProcess) {
						break;
					}

					idCount = 0;
					maxSeqId = getMaxSeqIdByAction(oifId, "select_contact");
					while (idCount < maxSeqId && maxSeqId != 0) {
						idCount++;
						tempJO = getProcessFields(processFieldsOb, "select_contact", idCount);

						if (!tempJO.isEmpty()) {

							resultFromMethod = addContactToBO(BOType, oifId, tempJO, cookie, boDataSource);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from Add Contact to BO (" + oifId + "): " + resultFromMethod);
								dBUtil.updateStatus(oifId, -1, "Return from Select Contact to BO: " + resultFromMethod,
										"selectContact");
								taskLog = resultFromMethod;
								stopProcess = true;
								break;
							}

						} else {
							logger.info("No more selected contacts for adding");
							// break;
						}

					}
					if (stopProcess) {
						break;
					}

					System.out.println("select_contact: " + maxSeqId + " >>> idCount:" + idCount);

					// Items and Groups

					if (BOType.equals("recordSchedule") || BOType.equals("DAL")) {

						itemsUuids = new HashMap<String, String>();

						// Item
						idCount = 0;

						maxSeqId = getMaxSeqIdByAction(oifId, "create_item");
						JSONObject userActionDataForItems;
						while (idCount < maxSeqId && maxSeqId != 0) {
							idCount++;

							userActionDataForItems = getUserActionHisByActionName(userActionHistoryList,
									"ITEM_" + idCount);
							itemId = createItem(processFieldsOb, BOType, oifId, BOId, idCount, boDataSource,
									inactiveItemsIds, userActionDataForItems, cookie);

							if (itemId.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from CreateItem (" + oifId + "): " + itemId);
								dBUtil.updateStatus(oifId, -1, "Return from Create Item : " + itemId, "createItem");
								taskLog = itemId;
								stopProcess = true;
								break;
							} else if (itemId != "") {
								resultFromMethod = addItemToBO(BOType, oifId, itemId, userActionDataForItems, cookie);

								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from Add Item to BO (" + oifId + "): " + resultFromMethod);
									dBUtil.updateStatus(oifId, -1, "Return from Add Item to BO: " + resultFromMethod,
											"addItem");
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}
								itemsUuids.put(String.valueOf(idCount), itemId);

							} else {
								logger.info("No more Items for adding");
								// break;
							}

						}
						if (stopProcess) {
							break;
						}
						System.out.println("create_item: " + maxSeqId + " >>> idCount:" + idCount);

						// Groups
						Map<String, String> groupsUuids = new HashMap<String, String>();
						idCount = 0;

						maxSeqId = getMaxSeqIdByAction(oifId, "create_group");
						while (idCount < maxSeqId && maxSeqId != 0) {
							idCount++;
							groupId = createGroup(processFieldsOb, BOType, oifId, idCount, itemsUuids, groupsUuids,
									boDataSource, userActionHistoryList, cookie);

							if (groupId.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from createGroup (" + oifId + "): " + groupId);
								dBUtil.updateStatus(oifId, -1, "Return from Create Group: " + groupId, "createGroup");
								taskLog = groupId;
								stopProcess = true;
								break;
							} else if (groupId == "") {
								logger.info("No more Groups for adding");
								// break;
							}
							groupsUuids.put(String.valueOf(idCount), groupId);

						}
						System.out.println("create_group: " + maxSeqId + " >>> idCount:" + idCount);
						if (stopProcess) {
							break;
						}
					}

					// Attachments
					idCount = 0;
					maxSeqId = getMaxSeqIdByAction(oifId, "att_data");
					while (idCount < maxSeqId && maxSeqId != 0) {
						idCount++;

						tempJO = getProcessFields(processFieldsOb, "att_data", idCount);
						if (tempJO.has(NARAConstants.FORM_DATA))
							tempJO = tempJO.getJSONObject(NARAConstants.FORM_DATA);

						if (tempJO.isEmpty()) {
							logger.info("No more Attachments for adding");
							// break;
						} else {
							attArray.put(tempJO);
						}

					}
					System.out.println("att_data: " + maxSeqId + " >>> idCount:" + idCount);

					if (!attArray.isEmpty()) {

						resultFromMethod = addAttToBO(BOType, oifId, attArray, userActionHistoryList, cookie);
						if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
							logger.error("Return from Add Attachments to BO (" + oifId + ") : " + resultFromMethod);
							dBUtil.updateStatus(oifId, -1, "Return from Add Attachments to BO: " + resultFromMethod,
									"addAttachments");
							taskLog = resultFromMethod;
							stopProcess = true;
							break;
						}

					} else {
						logger.info("No Attachments for adding");
					}

					// Additional Reviewers
					idCount = 0;
					maxSeqId = getMaxSeqIdByAction(oifId, "review_data");
					while (idCount < maxSeqId && maxSeqId != 0) {
						idCount++;
						tempJO = getProcessFields(processFieldsOb, "review_data", idCount);
						if (tempJO.has(NARAConstants.FORM_DATA))
							tempJO = tempJO.getJSONObject(NARAConstants.FORM_DATA);

						if (tempJO.isEmpty()) {
							logger.info("No more Reviewers for adding");
							// break;
						} else
							reviewArr.put(tempJO);

					}
					System.out.println("review_data: " + maxSeqId + " >>> idCount:" + idCount);

					if (!reviewArr.isEmpty()) {

						resultFromMethod = addReviewersToBO(BOType, oifId, reviewArr, cookie);
						if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
							logger.error("Return from Add Reviewers to BO (" + oifId + "): " + resultFromMethod);
							dBUtil.updateStatus(oifId, -1, "Return from Add Reviewers to BO: " + resultFromMethod,
									"addReviewers");
							taskLog = resultFromMethod;
							stopProcess = true;
							break;
						}

					} else {
						logger.info("No Reviewers for adding");
					}

					if (!stopProcess) {
						taskLog = taskJO.getString(NARAConstants.NAME) + " with Id: " + oifId;
						dBUtil.updateStatus(oifId, 3, "", action);
					}

					break;

				case NARAConstants.BO_ACTION_SUBMITBO:

					if (oifId == "") {
						logger.error("No BO ID");
						taskLog = "No BO ID";
						dBUtil.updateStatus(oifId, -1, "No BO ID", action);
						stopProcess = true;
						break;
					} else {

						if (pSubmitTask.isEmpty())
							pSubmitTask = taskJO.getString(NARAConstants.KEY);
						else if (pSubmitTask.equals(taskJO.getString(NARAConstants.KEY)))
							logger.error("Return from submitBO (" + oifId + ") (" + taskJO.getString(NARAConstants.NAME)
									+ ") this task is duplicated !!!! ");

						if (BOType.equals("recordSchedule") && taskJO.getString(NARAConstants.KEY).equals("APPROVE")
								&& itemsUuids != null && !itemsUuids.isEmpty() && !inactiveItemsIds.isEmpty())
							resultFromMethod = submitBO(BOType, oifId, taskJO, userActionHistoryList, cookie, true);
						else
							resultFromMethod = submitBO(BOType, oifId, taskJO, userActionHistoryList, cookie, false);

						if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
							logger.error("Return from submitBO (" + oifId + ") (" + taskJO.getString(NARAConstants.NAME)
									+ "): " + resultFromMethod);
							dBUtil.updateStatus(oifId, -1,
									"Return from Submit BO (" + taskJO.getString(NARAConstants.NAME)
											+ "): " + resultFromMethod,
									action);
							taskLog = resultFromMethod;
							stopProcess = true;
							break;
						}

						// Federal Register Section

						if (BOType.equals("recordSchedule") && taskJO.getString(NARAConstants.KEY).equals("ACCEPT")) {

							// Appraisal Memo
							idCount = 0;
							attArray = new JSONArray();
							maxSeqId = getMaxSeqIdByAction(oifId, "fed_att");
							while (idCount < maxSeqId && maxSeqId != 0) {
								idCount++;

								tempJO = getProcessFields(processFieldsOb, "fed_att", idCount);
								if (tempJO.has(NARAConstants.FORM_DATA))
									tempJO = tempJO.getJSONObject(NARAConstants.FORM_DATA);

								if (tempJO.isEmpty()) {
									logger.info("No more Federal Attachments for adding");
									// break;
								} else {
									attArray.put(tempJO);
								}

							}

							if (!attArray.isEmpty()) {

								resultFromMethod = addFederalAttToBO(BOType, oifId, attArray, userActionHistoryList,
										cookie);
								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from Add Federal Attachments to BO (" + oifId + ") : "
											+ resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from Add Federal Attachments to BO: " + resultFromMethod,
											"addFederalAttachments");
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}

							} else {
								logger.info("No Federal Attachments for adding");
							}

							// Create Federal Register Documents
							idCount = 0;
							JSONArray fedDocArray = new JSONArray();
							maxSeqId = getMaxSeqIdByAction(oifId, "fed_doc");
							while (idCount < maxSeqId && maxSeqId != 0) {
								idCount++;

								tempJO = getProcessFields(processFieldsOb, "fed_doc", idCount);
								if (tempJO.has(NARAConstants.FORM_DATA))
									tempJO = tempJO.getJSONObject(NARAConstants.FORM_DATA);

								if (tempJO.isEmpty()) {
									logger.info("No more Federal Documents for adding");
									// break;
								} else {
									fedDocArray.put(tempJO);
								}

							}

							if (!fedDocArray.isEmpty()) {

								resultFromMethod = addFederalDocToBO(BOType, oifId, fedDocArray, attArray,
										userActionHistoryList, cookie);
								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from Add Federal Documents to BO (" + oifId + ") : "
											+ resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from Add Federal Documents to BO: " + resultFromMethod,
											"addFederalDocToBO");
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}

							} else {
								logger.info("No Federal Attachments for adding");
							}

							// Federal Register Publication
							resultFromMethod = editFederalRegisterPublication(processFieldsOb, oifId, BOType,
									userActionHistoryList, cookie);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from Edit Federal Register Publication in BO (" + oifId + ") : "
										+ resultFromMethod);
								dBUtil.updateStatus(oifId, -1,
										"Return from Edit Federal Register Publication in BO: " + resultFromMethod,
										"editFederalRegisterPublication");
								taskLog = resultFromMethod;
								stopProcess = true;
								break;
							} else if (resultFromMethod.isEmpty()) {
								logger.info("No Federal Register Publication for adding");
							}

							// Federal Register Comment Period
							resultFromMethod = editFederalRegisterCommentPeriod(processFieldsOb, oifId, BOType,
									userActionHistoryList, cookie);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from Edit Federal Register Comment Period in BO (" + oifId + ") : "
										+ resultFromMethod);
								dBUtil.updateStatus(oifId, -1,
										"Return from Edit Federal Register Comment Period in BO: " + resultFromMethod,
										"editFederalRegisterCommentPeriod");
								taskLog = resultFromMethod;
								stopProcess = true;
								break;
							} else if (resultFromMethod.isEmpty()) {
								logger.info("No Federal Register Comment Period for adding");
							}

							// Executive Summary Overview
							resultFromMethod = addSummaryOverview(processFieldsOb, oifId, BOType, userActionHistoryList,
									cookie);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error(
										"Return from Add Summary Overview in BO (" + oifId + ") : " + resultFromMethod);
								dBUtil.updateStatus(oifId, -1,
										"Return from Add Summary Overview in BO: " + resultFromMethod,
										"addSummaryOverview");
								taskLog = resultFromMethod;
								stopProcess = true;
								break;
							} else if (resultFromMethod.isEmpty()) {
								logger.info("No Executive Summary Overview for adding");
							}

						}

						// Nara Only Attachment
						if ((BOType.equals("recordSchedule") && taskJO.getString(NARAConstants.KEY).equals("ACCEPT"))
								|| ((BOType.equals("DAL")) && taskJO.getString(NARAConstants.KEY).equals("APPROVE"))) {
							idCount = 0;
							attArray = new JSONArray();
							maxSeqId = getMaxSeqIdByAction(oifId, "nara_att");
							while (idCount < maxSeqId && maxSeqId != 0) {
								idCount++;

								tempJO = getProcessFields(processFieldsOb, "nara_att", idCount);
								if (tempJO.has(NARAConstants.FORM_DATA))
									tempJO = tempJO.getJSONObject(NARAConstants.FORM_DATA);

								if (tempJO.isEmpty())
									logger.info("No more Nara Only Attachment for adding");
								else
									attArray.put(tempJO);

							}
							System.out.println("nara_att: " + maxSeqId + " >>> idCount:" + idCount);

							if (!attArray.isEmpty()) {
								resultFromMethod = addNaraOnlyAttachment(BOType, oifId, attArray,
										userActionHistoryList);
								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from Add Nara Only Attachment to BO (" + oifId + "): "
											+ resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from Add Nara Only Attachment to BO: " + resultFromMethod,
											"addNaraOnlyAttachment");
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}
							} else {
								logger.info("No Nara Only Attachment for adding");
							}
						}

						// Modify BO & Post Approval & Inactivate Items
						if ((BOType.equals("recordSchedule") || BOType.equals("DAL"))
								&& taskJO.getString(NARAConstants.KEY).equals("APPROVE")) {

							// Modify BO
							resultFromMethod = modifyBO(processFieldsOb, BOType, oifId, userActionHistoryList);

							if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
								logger.error("Return from Modify BO (" + oifId + "): " + resultFromMethod);
								taskLog = resultFromMethod;
								dBUtil.updateStatus(oifId, -1, "Return from Modify BO: " + resultFromMethod,
										"modifyBO");
								stopProcess = true;
								break;
							}

							if (resultFromMethod.isEmpty()) {
								logger.info("No more data for Modify BO");
							}

							// Post Approval
							if (BOType.equals("recordSchedule")) {
								resultFromMethod = postApproval(processFieldsOb, BOType, oifId, userActionHistoryList);

								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from Post Approval (" + oifId + "): " + resultFromMethod);
									taskLog = resultFromMethod;
									dBUtil.updateStatus(oifId, -1, "Return from Post Approval: " + resultFromMethod,
											"postApproval");
									stopProcess = true;
									break;
								}

								if (resultFromMethod.isEmpty()) {
									logger.info("No data for Post Approval");
								}
							}

							// Inactivate Items

							if (!inactiveItemsIds.isEmpty()) {

								resultFromMethod = getItemsByBoIdFromNaraApi(oifId);

								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error(
											"Return from getItemsByBoIdFromNaraApi (" + oifId + "): "
													+ resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from getItemsByBoIdFromNaraApi (" + oifId + "): "
													+ resultFromMethod,
											action);
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}

								Map<String, String> itemUuidFromApi = getItemUuid(resultFromMethod);

								if (itemUuidFromApi == null || itemUuidFromApi.isEmpty()) {
									logger.error("Return from getItemUuid (" + oifId + "): " + resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from getItemUuid (" + oifId + "): " + resultFromMethod, action);
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}

								resultFromMethod = inactivateItems(oifId, itemUuidFromApi, inactiveItemsIds,
										processFieldsOb);
								if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
									logger.error("Return from inactivateItems (" + oifId + "): " + resultFromMethod);
									dBUtil.updateStatus(oifId, -1,
											"Return from inactivateItems (" + oifId + "): " + resultFromMethod, action);
									taskLog = resultFromMethod;
									stopProcess = true;
									break;
								}
							} else
								logger.info("No items for inactivate items task (" + oifId + ")");

						}

					}
					if (!stopProcess)
						taskLog = "Submit for (" + taskJO.getString(NARAConstants.NAME) + ")";

					break;

				case NARAConstants.BO_ACTION_REASSIGNBO:

					if (taskJO.getString(NARAConstants.TO_USER).isEmpty()) {
						logger.error("You need to assign user for the next task (" + oifId + ")");
						taskLog = "You need to assign user for the next task";
						dBUtil.updateStatus(oifId, -1, "You need to assign user for the next task", action);
						stopProcess = true;
						break;
					} else {
						resultFromMethod = reassignBO(BOType, oifId, taskJO.getString(NARAConstants.TO_USER),
								taskJO.getString(NARAConstants.COMMENTS), cookie);

						if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
							logger.error("Return from reassignBO (" + oifId + "): " + resultFromMethod);
							dBUtil.updateStatus(oifId, -1, "Return from reassignBO: " + resultFromMethod, action);
							taskLog = resultFromMethod;
							stopProcess = true;
							break;
						}

					}
					if (!stopProcess)
						taskLog = "Assign next task for " + taskJO.getString(NARAConstants.TO_USER);

					break;

				case NARAConstants.BO_ACTION_RETURNBO:

					if (taskJO.getString(NARAConstants.COMMENTS).isEmpty()) {
						logger.error("You need to add a reason as a comment to return the BO (" + oifId + ")");
						taskLog = "You need to add a reason as a comment to return the BO";
						dBUtil.updateStatus(oifId, -1,
								"Return from returnBO: You need to add a reason as a comment to return the BO", action);
						stopProcess = true;
						break;
					} else {
						resultFromMethod = returnBO(BOType, oifId, taskJO.getString(NARAConstants.KEY),
								taskJO.getString(NARAConstants.COMMENTS), userActionHistoryList, cookie);
						if (resultFromMethod.startsWith(NARAConstants.ERROR)) {
							logger.error("Return from returnBO (" + oifId + "): " + resultFromMethod);
							dBUtil.updateStatus(oifId, -1, "Return from returnBO: " + resultFromMethod, action);
							taskLog = resultFromMethod;
							stopProcess = true;
							break;
						}

					}
					if (!stopProcess)
						taskLog = "The task returned for revision";

					break;

				default:

					logger.error("No Action Selected For This Task (" + taskJO.getString(NARAConstants.NAME) + ") in ("
							+ oifId + ")");
					taskLog = "No Action Selected For This Task (" + taskJO.getString(NARAConstants.NAME) + ") !!! ";
					dBUtil.updateStatus(BOId, -1,
							"No Action Selected For This Task (" + taskJO.getString(NARAConstants.NAME) + ") !!! ",
							"noAction");
					stopProcess = true;
					break;
			}

			arrForLog.put(action + " >>> " + taskLog);

		}

		if (!stopProcess)
			dBUtil.updateStatus(oifId, 4, "", env.getProperty("nara.mig.status.completed"));

		if (oifId.isEmpty() && arrForLog.isEmpty()) {
			dBUtil.updateStatus(BOId, -1, "An error occurred, please see the log file.", "");
			oifId = BOId;
			arrForLog.put("An error occurred, please see the log file.");
		}

		List<Map<String, Object>> tempListForActionResult = new ArrayList<Map<String, Object>>();
		tempJO = new JSONObject();
		tempJO.put("Bo ID", oifId);
		tempJO.put("logs", arrForLog);

		tempListForActionResult.add(tempJO.toMap());

		logger.info(tempListForActionResult.toString());

		if (!paginationUtil.isSkip() && paginationUtil.getStatus() == 0) {

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
					BoIDs.put(humanReadableId, id);

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
			BoIDs.put(childHumanReadableId, childId);

		if (child.has("children")) {
			JSONArray grandChildren = child.getJSONArray("children");
			if (grandChildren != null)
				for (int i = 0; i < grandChildren.length(); i++)
					processChild(grandChildren.getJSONObject(i), BoIDs);
		}

	}

	private String addNaraOnlyAttachment(String BOType, String oifId, JSONArray attArray,
			JSONArray userActionHistoryList) {

		String naraOnlyAttachmentUser = "", finalResults = "";

		if (BOType.equals("recordSchedule"))
			naraOnlyAttachmentUser = boTaskRepo.getUserNameByBOandTask(oifId, "APPRAISER_SUPERVISOR_CONCUR");
		else if (BOType.equals("DAL"))
			naraOnlyAttachmentUser = env.getProperty("nara.era2.userName.LRS.NaraOnlyAttachmentUser");

		if (naraOnlyAttachmentUser != null && naraOnlyAttachmentUser.isEmpty()) {
			logger.info("Info: Login for Nara Only Attachment for the BO: (" + oifId + ") is failed for this user: "
					+ naraOnlyAttachmentUser);
			return finalResults;
		}

		List<String> cookie = apiUtil.getCookies(naraOnlyAttachmentUser);

		if (cookie.isEmpty()) {
			logger.error("ERROR: Login for Nara Only Attachment for the BO: (" + oifId + ") is failed for this user: "
					+ naraOnlyAttachmentUser);
			dBUtil.updateStatus(oifId, -1, "Login for Nara Only Attachment for the BO: (" + oifId
					+ ") is failed for this user: " + naraOnlyAttachmentUser, "addNaraOnlyAttachment");
			return "ERROR: Login for Nara Only Attachment for the BO: (" + oifId + ") is failed for this user: "
					+ naraOnlyAttachmentUser;
		}

		JSONObject attachments = new JSONObject();

		JSONObject processFieldsMap = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addNaraOnlyAttachment this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addNaraOnlyAttachment this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		JSONObject userActionData = getUserActionHisByActionName(userActionHistoryList, "NaraOnlyAttachment"),
				reqJOData = new JSONObject();

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("Nara Only Attachment doesn't exist in user action history list");

		if (!reqJOData.isEmpty())
			processFieldsMap.put("childId", reqJOData.toString());
		else
			processFieldsMap.put("childId", "");

		processFieldsMap.put("view", "NaraOnlyAttachment");
		processFieldsMap.put(NARAConstants.ACTION, JSONObject.NULL);
		attachments.put("UUPxUr_v1", attArray);
		processFieldsMap.put(NARAConstants.FORM_DATA, attachments);

		processFieldsMap.put("updateType", "NARA_UPDATE_ATT");
		System.out.println("processFieldsMap: " + processFieldsMap);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addNaraOnlyAttachment (" + oifId + ") : " + reqResult;
			logger.error("ERROR in addNaraOnlyAttachment (" + oifId + ") : " + reqResult);

		} else {
			finalResults = reqResult;
			logger.info("Nara Only Attachment have been successfully added to the BO(" + oifId + ")");
		}

		return finalResults;

	}

	private String addSummaryOverview(JSONObject processFieldsList, String boId, String BOType,
			JSONArray userActionHistoryList, List<String> cookie) {

		JSONObject userActionData, reqJOData = new JSONObject(),
				processFieldsMap = getProcessFields(processFieldsList, "SummaryOverview", 0);

		if (processFieldsMap.isEmpty())
			return "";

		userActionData = getUserActionHisByActionName(userActionHistoryList, "SummaryOverview");
		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (SummaryOverview) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			processFieldsMap.put("childId", reqJOData.toString());
		}

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addSummaryOverview this BO (" + boId + ") type (" + BOType + ") is not found");
			return "ERROR in addSummaryOverview this BO (" + boId + ") type (" + BOType + ") is not found";
		}

		String finalResults = "";

		processFieldsMap.put("view", "RSExecutiveSummaryOverview");
		processFieldsMap.put("action", JSONObject.NULL);
		processFieldsMap.put("updateType", "EDIT");

		System.out.println("processFieldsMap: " + processFieldsMap);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + boId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addSummaryOverview (" + boId + ") : " + reqResult;
			logger.error("ERROR in addSummaryOverview (" + boId + ") : " + reqResult);
		} else {
			finalResults = reqResult;
			logger.info("The Summary Overview have been successfully Updated to the BO(" + boId + ")");
		}

		return finalResults;

	}

	private String editFederalRegisterCommentPeriod(JSONObject processFieldsList, String boId, String BOType,
			JSONArray userActionHistoryList, List<String> cookie) {

		JSONObject userActionData, reqJOData = new JSONObject(),
				processFieldsMap = getProcessFields(processFieldsList, "FedRegComment", 0);

		if (processFieldsMap.isEmpty())
			return "";

		userActionData = getUserActionHisByActionName(userActionHistoryList, "FedRegComment");
		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (FedRegComment) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			processFieldsMap.put("childId", reqJOData.toString());
		}

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in editFederalRegisterCommentPeriod this BO (" + boId + ") type (" + BOType
					+ ") is not found");
			return "ERROR in editFederalRegisterCommentPeriod this BO (" + boId + ") type (" + BOType
					+ ") is not found";
		}

		processFieldsMap.put("view", "GeneralFederalRegister");
		processFieldsMap.put("action", JSONObject.NULL);
		processFieldsMap.put("updateType", "EDIT");

		String finalResults = "";

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + boId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in editFederalRegisterCommentPeriod (" + boId + ") : " + reqResult;
			logger.error("ERROR in editFederalRegisterCommentPeriod (" + boId + ") : " + reqResult);
		} else {
			finalResults = reqResult;
			logger.info("The Federal Register Comment Period have been successfully Updated to the BO(" + boId + ")");
		}

		return finalResults;

	}

	private String editFederalRegisterPublication(JSONObject processFieldsList, String boId, String BOType,
			JSONArray userActionHistoryList, List<String> cookie) {

		JSONObject userActionData, reqJOData = new JSONObject(),
				processFieldsMap = getProcessFields(processFieldsList, "FedRegPublication", 0);

		if (processFieldsMap.isEmpty())
			return "";

		userActionData = getUserActionHisByActionName(userActionHistoryList, "FedRegPublication");
		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (FedRegPublication) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			processFieldsMap.put("childId", reqJOData.toString());
		}

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in editFederalRegisterPublication this BO (" + boId + ") type (" + BOType
					+ ") is not found");
			return "ERROR in editFederalRegisterPublication this BO (" + boId + ") type (" + BOType + ") is not found";
		}

		processFieldsMap.put("view", "FederalRegisterPublication");
		processFieldsMap.put("action", JSONObject.NULL);
		processFieldsMap.put("updateType", "EDIT");

		String finalResults = "";

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + boId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in editFederalRegisterPublication (" + boId + ") : " + reqResult;
			logger.error("ERROR in editFederalRegisterPublication (" + boId + ") : " + reqResult);
		} else {
			finalResults = reqResult;
			logger.info("The Federal Register Publication have been successfully Updated to the BO(" + boId + ")");
		}

		return finalResults;

	}

	private String returnBO(String BOType, String oifId, String actionType, String comments,
			JSONArray userActionHistoryList, List<String> cookie) {

		String finalResults = "";

		JSONObject processFieldsMap = new JSONObject(), temp = new JSONObject(), userActionData;

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType)) {

			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
			processFieldsMap.put("childId", "");
			processFieldsMap.put("updateType", "ACTION");
			processFieldsMap.put("view", "ReturnComment");
			processFieldsMap.put(NARAConstants.ACTION, actionType);
			temp.put("lWf25A_v1", comments);
			processFieldsMap.put(NARAConstants.FORM_DATA, temp);
		} else {
			logger.error("ERROR in returnBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in returnBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		userActionData = getUserActionHisByActionName(userActionHistoryList, actionType);
		if (userActionData != null && !userActionData.isEmpty())
			processFieldsMap.put("childId", updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (" + actionType + ") doesn't exist in user action history list");

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in returnBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in returnBO (" + oifId + ") : " + reqResult);
		} else {
			finalResults = "The BO(" + oifId + ") has been returned successfully";
			logger.info("The BO(" + oifId + ") has been returned successfully");
		}

		return finalResults;

	}

	private JSONObject getUserActionHisByActionName(JSONArray userActionHistoryList, String actionName) {
		JSONObject obj;
		for (int i = 0; i < userActionHistoryList.length(); i++) {
			obj = userActionHistoryList.getJSONObject(i);
			if (obj.getString(NARAConstants.NAME).equals(actionName)) {
				userActionHistoryList.remove(i);
				logger.info("The action (" + actionName + ") has been found: " + obj.toString());
				return obj;
			}
		}
		return null;
	}

	private JSONObject updateUserActionInfo(JSONObject userActionInfo) {

		JSONObject actionInfo = new JSONObject();

		if (!userActionInfo.getString(NARAConstants.USERNAME).isEmpty())
			actionInfo.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USERNAME,
					userActionInfo.getString(NARAConstants.USERNAME));

		if (!userActionInfo.getString(NARAConstants.USER_ID).isEmpty())
			actionInfo.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USERID,
					userActionInfo.getString(NARAConstants.USER_ID));

		if (!userActionInfo.getString(NARAConstants.FIRST_NAME).isEmpty()
				|| !userActionInfo.getString(NARAConstants.LAST_NAME).isEmpty())
			actionInfo.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USER_NAME,
					userActionInfo.getString(NARAConstants.FIRST_NAME) + " "
							+ userActionInfo.getString(NARAConstants.LAST_NAME));

		if (!userActionInfo.getString(NARAConstants.TIME_STAMP).isEmpty())
			actionInfo.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_TIMESTAMP,
					userActionInfo.getString(NARAConstants.TIME_STAMP));

		return actionInfo;
	}

	private JSONObject createBO(JSONObject processFieldsList, String oldBOId, String BOType,
			JSONArray userActionHistoryList, List<String> cookie) {

		JSONObject jsonResult = new JSONObject(), userActionData, reqJOData = new JSONObject(),
				apiReqBody = getProcessFields(processFieldsList, NARAConstants.BO_ACTION_CREATEBO, 0);

		if (apiReqBody.isEmpty())
			return jsonResult;

		if (!oldBOId.isEmpty())
			reqJOData.put(NARAConstants.HUMAN_READABLE_ID, oldBOId);
		else
			logger.info("Return from CreateBo: The original BO_Id isn't found in the XML");

		userActionData = getUserActionHisByActionName(userActionHistoryList, "CREATE");
		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (Create) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		}

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in createBO this BO (" + oldBOId + ") type (" + BOType + ") is not found");
			return new JSONObject().put(NARAConstants.ERROR,
					"ERROR in createBO this BO (" + oldBOId + ") type (" + BOType + ") is not found");
		}

		CloseableHttpClient client = HttpClients.createDefault();
		try {

			HttpPost httpPost = new HttpPost(
					env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
							+ env.getProperty("nara.era2.businessObjects.createBO"));

			StringEntity entity = new StringEntity(apiReqBody.toString(), "UTF-8");
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			for (String s : cookie) {
				httpPost.addHeader("Cookie", s);
			}

			httpPost.addHeader("t", String.valueOf(System.currentTimeMillis()));

			System.out.println("httpPost: " + httpPost + "\n" + apiReqBody);

			CloseableHttpResponse response = client.execute(httpPost);

			String result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {

				jsonResult = new JSONObject(result);

				if (jsonResult.has(NARAConstants.OIF_ID)) {
					logger.info("The BO created successfully with Id:" + jsonResult.getString(NARAConstants.OIF_ID));

				} else {
					jsonResult.put(NARAConstants.ERROR, "ERROR BO ID doesn't exist " + result);
					logger.error("ERROR in createBO (" + oldBOId + "): " + result);
				}

			} else {
				jsonResult.put(NARAConstants.ERROR, "ERROR in Create_BO with code (" + statusCode + "): " + result);

				logger.error("ERROR in Create_BO (" + oldBOId + ") with code (" + statusCode + "): " + result);
			}

		} catch (IOException ex) {

			jsonResult.put(NARAConstants.ERROR, "ERROR in Create_BO: " + ex.getMessage());
			logger.error("ERROR in createBO (" + oldBOId + "): " + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				jsonResult.put(NARAConstants.ERROR, "ERROR in Create_BO: " + ex.getMessage());
				logger.error("ERROR in createBO (" + oldBOId + "): " + ex.getMessage());
			}
		}
		return jsonResult;

	}

	private String updateBO(JSONObject allProcessFields, String BOType, String oifId, JSONArray userActionHistoryList,
			List<String> cookie) {

		String finalResults = "";

		JSONObject reqJOData = new JSONObject(),
				apiReqBody = getProcessFields(allProcessFields, NARAConstants.BO_ACTION_UPDATEBO, 0), userActionData;

		if (apiReqBody.isEmpty()) {
			return finalResults;
		}

		// String returnFromCheckFieldValue =
		// checkFieldsValidate(apiReqBody.getJSONObject(NARAConstants.FORM_DATA));

		// if (!returnFromCheckFieldValue.isEmpty()) {
		// finalResults = "ERROR return from validating field value : " +
		// returnFromCheckFieldValue;
		// return finalResults;
		// }

		userActionData = getUserActionHisByActionName(userActionHistoryList, "UPDATE");

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (Update) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		} else
			apiReqBody.put("childId", "");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType)) {
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
			BOType = NARAConstants.BO_TYPES_MAP.get(BOType);
		} else {
			logger.error("ERROR in updateBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in updateBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		apiReqBody.put("updateType", "EDIT");
		apiReqBody.put("action", JSONObject.NULL);

		List<String> sectionList = dBUtil.getSectionsByAction(BOType, NARAConstants.BO_ACTION_UPDATEBO);

		JSONObject formData = apiReqBody.getJSONObject(NARAConstants.FORM_DATA), temp;
		List<String> fieldList;

		logger.info("sectionList: " + sectionList);
		logger.info("formData: " + formData);

		for (String sec : sectionList) {
			temp = new JSONObject();

			logger.info("The section (" + sec + ")");

			fieldList = dBUtil.getFieldsBySection(BOType, NARAConstants.BO_ACTION_UPDATEBO, sec);

			logger.info("fieldList: " + fieldList);

			for (String fieldId : fieldList)
				if (formData.has(fieldId))
					temp.put(fieldId, formData.get(fieldId));

			if (temp.isEmpty())
				continue;
			else
				logger.info("The formDate fields: " + temp);

			apiReqBody.put("view", sec);
			apiReqBody.put(NARAConstants.FORM_DATA, temp);

			String reqResult = httpPathch(
					env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
							+ env.getProperty("nara.era2.businessObjects.updateBO") + oifId,
					apiReqBody.toString(), cookie);

			if (reqResult.startsWith("ERROR")) {
				finalResults = "ERROR in updateBO (" + oifId + ") section (" + sec + ") : " + reqResult;
				logger.error("ERROR in updateBO (" + oifId + ") section (" + sec + ") : " + reqResult);
				break;
			} else {
				finalResults = "The BO(" + oifId + ") has been updated successfully";
				logger.info("The BO(" + oifId + ") has been updated successfully");
			}

		}

		return finalResults;

	}

	private String modifyBO(JSONObject allProcessFields, String BOType, String oifId, JSONArray userActionHistoryList) {

		String finalResults = "";

		int idCount = 0, maxSeqId = getMaxSeqIdByAction(oifId, "Modify_BO");
		boolean hasModifyData = false;

		String userForModify = "";

		if (BOType.equals("recordSchedule"))
			userForModify = boTaskRepo.getUserNameByBOandTask(oifId, "APPRAISER_SUPERVISOR_CONCUR");

		if (BOType.equals("DAL"))
			userForModify = boTaskRepo.getUserNameByBOandTask(oifId, "APPROVE");

		List<String> cookie = apiUtil.getCookies(userForModify);

		if (cookie.isEmpty()) {
			logger.error(
					"ERROR: Login for Modify BO for the BO: (" + oifId + ") is failed for this user: " + userForModify);
			dBUtil.updateStatus(oifId, -1,
					"Login for Modify BO for the BO: (" + oifId + ") is failed for this user: " + userForModify,
					"modifyBO");
			return "ERROR: Login for Modify BO for the BO: (" + oifId + ") is failed for this user: " + userForModify;
		}

		Map<Integer, JSONObject> userActionHistoryMap = new HashMap<Integer, JSONObject>();

		int sleepTime = env.getProperty("nara.sleepTime").isEmpty() ? 5000
				: Integer.parseInt(env.getProperty("nara.sleepTime"));

		while (idCount < maxSeqId && maxSeqId != 0) {
			idCount++;

			JSONObject apiReqBody = getProcessFields(allProcessFields, "Modify_BO", idCount), userActionData, formData;

			String boidModified;
			hasModifyData = false;

			logger.info("apiReqBody: " + apiReqBody);

			if (apiReqBody.has("formData")) {
				formData = apiReqBody.getJSONObject("formData");
				if (!formData.isEmpty()) {
					apiReqBody.put("formData", new JSONObject().put("RNcm02_v1", new JSONArray().put(formData)));
					hasModifyData = true;
				}

			}

			logger.info("apiReqBody: " + apiReqBody);

			if (hasModifyData) {

				userActionData = getUserActionHisByActionName(userActionHistoryList, "ModifyBO_" + idCount);

				finalResults = createModifyBO(BOType, oifId, cookie, null);

				if (finalResults.startsWith(NARAConstants.ERROR))
					return finalResults;

				boidModified = finalResults;

				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				finalResults = createRevisionHistory(boidModified, apiReqBody, BOType, cookie, userActionData);

				if (finalResults.startsWith(NARAConstants.ERROR))
					return finalResults;

				finalResults = confirmModifyBO(BOType, boidModified, cookie, null);

				if (finalResults.startsWith(NARAConstants.ERROR))
					return finalResults;

				// get proInstanceID by processName

				userActionHistoryMap.put(idCount, userActionData);

				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}

		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (!userActionHistoryMap.isEmpty())
			finalResults = updateUserActionInfo(oifId, userActionHistoryMap);

		if (finalResults.startsWith(NARAConstants.ERROR))
			return finalResults;

		return finalResults;

	}

	private String updateUserActionInfo(String oifId, Map<Integer, JSONObject> userActionHistoryMap) {

		String proInstId = apsApiServiceImp.getProcessInstanceIdByProcessName(oifId);

		if (proInstId.startsWith("ERROR")) {
			return proInstId;
		} else {

			String taskId = apsApiServiceImp.getActiveTaskIdByProcessInstanceId(proInstId);

			if (taskId.startsWith("ERROR")) {
				return taskId;
			}

			String formdataVariableData = apsApiServiceImp.getfromDataVarFromTaskFromByTaskId(taskId);

			if (formdataVariableData.startsWith("ERROR")) {
				return formdataVariableData;
			}

			JSONObject formdataVariableDataJson = new JSONObject(formdataVariableData), tempAction, tempActionHistory;

			JSONArray userActionFormDataArr = new JSONArray(), newUserActionFormDataArr = new JSONArray();

			if (formdataVariableDataJson.has("H11Kao_v1")) {
				userActionFormDataArr = new JSONArray(formdataVariableDataJson.getString("H11Kao_v1"));
				int count = 0;
				for (Object object : userActionFormDataArr) {
					tempAction = (JSONObject) object;

					if (tempAction.has("NCLTJm_v1")
							&& (tempAction.getString("NCLTJm_v1").equals("Modify Records Schedule")
									|| tempAction.getString("NCLTJm_v1").equals("Confirm Modifications"))) {

						if (tempAction.getString("NCLTJm_v1").equals("Modify Records Schedule"))
							count++;

						logger.info("Update Modify Records Schedule User Action History with count = " + count);
						tempActionHistory = userActionHistoryMap.get(count);

						if (!tempActionHistory.getString("username").isEmpty())
							tempAction.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USERNAME,
									tempActionHistory.getString("username"));

						if (!tempActionHistory.getString("userId").isEmpty())
							tempAction.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USERID,
									tempActionHistory.getString("userId"));

						if (!tempActionHistory.getString("firstName").isEmpty()
								|| !tempActionHistory.getString("lastName").isEmpty())
							tempAction.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_USER_NAME,
									tempActionHistory.getString("firstName") + " "
											+ tempActionHistory.getString("lastName"));

						// if (!tempActionHistory.getString("timestamp").isEmpty())
						// tempAction.put(NARAConstants.FORM_DATA_USER_ACTION_HISTORY_TIMESTAMP,
						// tempActionHistory.getString("timestamp"));

					}

					newUserActionFormDataArr.put(tempAction);

				}

				if (!newUserActionFormDataArr.isEmpty()) {
					formdataVariableDataJson.put("H11Kao_v1", newUserActionFormDataArr.toString());
					String updateFormDataResult = apsApiServiceImp.updateFormDataVariableByTaskId(taskId,
							formdataVariableDataJson.toString());

					if (updateFormDataResult.startsWith("ERROR"))
						return updateFormDataResult;

					String saveForm = apsApiServiceImp.saveFormDataVariableByTaskId(taskId);

					if (saveForm.startsWith("ERROR"))
						return saveForm;

				} else
					logger.info("No user action history found in form data for Modify BO");

			}

		}

		return "SUCCESS";
	}

	private String createModifyBO(String BOType, String oifId, List<String> cookie, JSONObject userActionData) {

		JSONObject reqJOData = new JSONObject(), apiReqBody = new JSONObject();
		String finalResults = "";

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (ModifyBO) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		} else
			apiReqBody.put("childId", "");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType)) {
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		} else {
			logger.error("ERROR in Modify this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in Modify this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		if (BOType.equals("recordSchedule"))
			apiReqBody.put("view", "RecordsSchedule");

		if (BOType.equals("DAL"))
			apiReqBody.put("view", "DAL");

		apiReqBody.put("updateType", "APPRAISER_MODIFY");
		apiReqBody.put("action", JSONObject.NULL);
		apiReqBody.put("formData", new JSONObject());

		String reqResult = httpPathch(
				env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
						+ env.getProperty("nara.era2.businessObjects.updateBO") + oifId,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in createModifyBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in createModifyBO (" + oifId + ") : " + reqResult);

		} else {
			finalResults = oifId + "-Modifying";
			logger.info("A modified BO for BO ID (" + oifId + ") has been created successfully");
		}

		return finalResults;

	}

	private String confirmModifyBO(String BOType, String boidModified, List<String> cookie, JSONObject userActionData) {
		JSONObject reqJOData = new JSONObject(), apiReqBody = new JSONObject();
		String finalResults = "";

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (ModifyBO) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		} else
			apiReqBody.put("childId", "");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType)) {
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		} else {
			logger.error("ERROR in Modify this BO (" + boidModified + ") type (" + BOType + ") is not found");
			return "ERROR in Modify this BO (" + boidModified + ") type (" + BOType + ") is not found";
		}

		apiReqBody.put("view", "RecordsScheduleApprovedModifying");

		apiReqBody.put("updateType", "CONFIRM");
		apiReqBody.put("action", "CONFIRMATION_MODIFYING");
		apiReqBody.put("formData", new JSONObject());

		String reqResult = httpPathch(
				env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
						+ env.getProperty("nara.era2.businessObjects.updateBO") + boidModified,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in confirmModifyBO (" + boidModified + ") : " + reqResult;
			logger.error("ERROR in confirmModifyBO (" + boidModified + ") : " + reqResult);

		} else {
			finalResults = "The BO(" + boidModified + ") has been confirmed successfully";
			logger.info("The BO(" + boidModified + ") has been confirmed successfully");
		}

		return finalResults;

	}

	private String createRevisionHistory(String boidModified, JSONObject apiReqBody, String BOType, List<String> cookie,
			JSONObject userActionData) {

		JSONObject reqJOData = new JSONObject();
		String finalResults = "";

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (ModifyBO) doesn't exist in user action history list");

		if (!reqJOData.isEmpty())
			apiReqBody.put("childId", reqJOData.toString());
		else
			apiReqBody.put("childId", "");

		apiReqBody.put("boType", "RECORDS_SCHEDULE");
		apiReqBody.put("view", "RecordsSchedule");
		apiReqBody.put("updateType", "EDIT");
		apiReqBody.put("action", JSONObject.NULL);

		String reqResult = httpPathch(
				env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
						+ env.getProperty("nara.era2.businessObjects.updateBO") + boidModified,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {

			finalResults = "ERROR in createRevisionHistory (" + boidModified + ") : " + reqResult;
			logger.error("ERROR in createRevisionHistory (" + boidModified + ") : " + reqResult);

		} else {

			finalResults = "The BO(" + boidModified + ") has been updated successfully";
			logger.info("The BO(" + boidModified + ") has been updated successfully");
		}

		return finalResults;

	}

	private String postApproval(JSONObject allProcessFields, String BOType, String oifId,
			JSONArray userActionHistoryList) {

		String appraiserSupervisorUser = boTaskRepo.getUserNameByBOandTask(oifId, "APPRAISER_SUPERVISOR_CONCUR"),
				finalResults = "";

		if (appraiserSupervisorUser != null && appraiserSupervisorUser.isEmpty()) {
			logger.info("Info: Login for post Approval for the BO: (" + oifId + ") is failed for this user: "
					+ appraiserSupervisorUser);
			return finalResults;
		}

		List<String> cookie = apiUtil.getCookies(appraiserSupervisorUser);

		if (cookie.isEmpty()) {
			logger.error("ERROR: Login for post Approval for the BO: (" + oifId + ") is failed for this user: "
					+ appraiserSupervisorUser);
			dBUtil.updateStatus(oifId, -1, "Login for Post Approval for the BO: (" + oifId
					+ ") is failed for this user: " + appraiserSupervisorUser, "postApproval");
			return "ERROR: Login for Post Approval for the BO: (" + oifId + ") is failed for this user: "
					+ appraiserSupervisorUser;
		}

		JSONObject reqJOData = new JSONObject(), apiReqBody = getProcessFields(allProcessFields, "PostApproval", 0),
				userActionData;

		if (apiReqBody.isEmpty())
			return finalResults;

		userActionData = getUserActionHisByActionName(userActionHistoryList, "PostApproval");

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("This action (PostApproval) doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		} else
			apiReqBody.put("childId", "");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType)) {
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
			BOType = NARAConstants.BO_TYPES_MAP.get(BOType);
		} else {
			logger.error("ERROR in PostApproval this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in PostApproval this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		apiReqBody.put("updateType", "POST_APPROVAL_EDIT");
		apiReqBody.put("action", JSONObject.NULL);
		apiReqBody.put("view", "RsPostApproval");

		String reqResult = httpPathch(
				env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
						+ env.getProperty("nara.era2.businessObjects.updateBO") + oifId,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in PostApproval (" + oifId + ") : " + reqResult;
			logger.error("ERROR in PostApproval (" + oifId + ") : " + reqResult);

		} else {
			finalResults = "The Post Approval for BO(" + oifId + ") has been updated successfully";
			logger.info("The Post Approval for BO(" + oifId + ") has been updated successfully");
		}

		return finalResults;

	}

	private String reassignBO(String BOType, String oifId, String userId, String comments, List<String> cookie) {

		String finalResults = "";

		JSONObject processFieldsMap = new JSONObject(), temp = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in reassignBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in reassignBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		processFieldsMap.put("updateType", "REASSIGN");
		processFieldsMap.put("childId", userId);
		temp.put("AssignComment", comments);
		processFieldsMap.put(NARAConstants.FORM_DATA, temp);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in reassignBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in reassignBO (" + oifId + ")  : " + reqResult);

		} else {
			finalResults = "The BO(" + oifId + ") has been assigned to " + userId + " successfully";
			logger.info("The BO(" + oifId + ") has been assigned to " + userId + " successfully");

		}

		return finalResults;

	}

	private String createGroup(JSONObject processFields, String BOType, String oifId, int id,
			Map<String, String> itemIds, Map<String, String> groupIds, String boDataSource,
			JSONArray userActionHistoryList, List<String> cookie) {

		String finalResults = "", groupOrder = "";

		JSONObject apiReqBody = getProcessFields(processFields, "create_group", id), formData = new JSONObject();

		if (apiReqBody.isEmpty()) {
			return finalResults;
		}

		if (boDataSource.equals("DB")) {
			if (apiReqBody.has(NARAConstants.FORM_DATA)) {
				formData = apiReqBody.getJSONObject(NARAConstants.FORM_DATA);
				apiReqBody.remove(NARAConstants.FORM_DATA);

				if (formData.has("groupName"))
					apiReqBody.put("groupName", formData.get("groupName"));

				if (formData.has("groupDes"))
					apiReqBody.put("groupDes", formData.get("groupDes"));

				if (formData.has("itemsIds"))
					apiReqBody.put("itemsIds", formData.get("itemsIds"));

				if (formData.has("groupsIds"))
					apiReqBody.put("groupsIds", formData.get("groupsIds"));

				if (formData.has("groupOrder"))
					apiReqBody.put("groupOrder", formData.get("groupOrder"));

				formData = new JSONObject();
			}
		}

		if (apiReqBody.has("groupName") && !apiReqBody.isNull("groupName")
				&& !apiReqBody.getString("groupName").isEmpty()) {
			formData.put("ZirBam_v1", apiReqBody.getString("groupName"));

			apiReqBody.remove("groupName");
		} else {
			return "ERROR in Create Group the Group Name is missing";
		}

		if (apiReqBody.has("groupOrder") && !apiReqBody.isNull("groupOrder")
				&& !apiReqBody.getString("groupOrder").isEmpty()) {
			groupOrder = apiReqBody.getString("groupOrder");
			apiReqBody.remove("groupOrder");
		}

		if (groupOrder.isEmpty()) {
			List<String> uuids = new ArrayList<String>();

			if (apiReqBody.has("itemsIds") && !apiReqBody.isNull("itemsIds")
					&& !apiReqBody.getString("itemsIds").isEmpty()) {
				String[] ids = apiReqBody.getString("itemsIds").split(",");
				for (int i = 0; i < ids.length; i++)
					uuids.add(itemIds.get(ids[i]));

				apiReqBody.remove("itemsIds");

			}

			if (apiReqBody.has("groupsIds") && !apiReqBody.isNull("groupsIds")
					&& !apiReqBody.getString("groupsIds").isEmpty()) {
				String[] ids = apiReqBody.getString("groupsIds").split(",");
				for (int i = 0; i < ids.length; i++)
					uuids.add(groupIds.get(ids[i]));

				apiReqBody.remove("groupsIds");
			}

			if (!uuids.isEmpty()) {
				String uuidStr = "";
				for (int i = 0; i < uuids.size(); i++) {
					uuidStr += uuids.get(i);
					if (i + 1 < uuids.size())
						uuidStr += ",";
				}
				formData.put("tDxZtV_v1", "Yes");
				formData.put("3HE9Da_v1", uuidStr);

			} else
				formData.put("tDxZtV_v1", "No");

		} else {
			List<String> itemsUuids = new ArrayList<String>();
			List<String> groupsUuids = new ArrayList<String>();

			if (apiReqBody.has("itemsIds") && !apiReqBody.isNull("itemsIds")
					&& !apiReqBody.getString("itemsIds").isEmpty()) {

				String[] ids = apiReqBody.getString("itemsIds").split(",");
				for (int i = 0; i < ids.length; i++)
					itemsUuids.add(itemIds.get(ids[i]));

				apiReqBody.remove("itemsIds");

			}

			if (apiReqBody.has("groupsIds") && !apiReqBody.isNull("groupsIds")
					&& !apiReqBody.getString("groupsIds").isEmpty()) {
				String[] ids = apiReqBody.getString("groupsIds").split(",");
				for (int i = 0; i < ids.length; i++)
					groupsUuids.add(groupIds.get(ids[i]));

				apiReqBody.remove("groupsIds");
			}

			List<String> groupOrderList = new ArrayList<String>(Arrays.asList(groupOrder.split(",")));

			if (!groupOrderList.isEmpty()) {
				String uuidStr = "";
				for (int i = 0; i < groupOrderList.size(); i++) {

					if (groupOrderList.get(i).equals("I")) {
						uuidStr += itemsUuids.get(0);
						itemsUuids.remove(0);
					}

					if (groupOrderList.get(i).equals("G")) {
						uuidStr += groupsUuids.get(0);
						groupsUuids.remove(0);
					}

					if (i + 1 < groupOrderList.size())
						uuidStr += ",";
				}

				formData.put("tDxZtV_v1", "Yes");
				formData.put("3HE9Da_v1", uuidStr);

			} else
				formData.put("tDxZtV_v1", "No");

		}

		if (apiReqBody.has("groupDes") && !apiReqBody.isNull("groupDes")
				&& !apiReqBody.getString("groupDes").isEmpty()) {

			formData.put("yGnU3t_v1", apiReqBody.getString("groupDes"));

			apiReqBody.remove("groupDes");
		}

		JSONObject userActionData = getUserActionHisByActionName(userActionHistoryList, "GROUP_" + id),
				reqJOData = new JSONObject();
		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("GROUP_" + id + " doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			apiReqBody.put("childId", reqJOData.toString());
		}

		apiReqBody.put("updateType", "CREATE_GROUP");
		apiReqBody.put("view", "CreateGroup");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			apiReqBody.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in Create Group this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in Create Group this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		apiReqBody.put("formData", formData);

		JSONObject jsonResult = new JSONObject();

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			logger.error("ERROR in Create Group (" + oifId + ") : " + reqResult);
			finalResults = "ERROR in Create Group (" + oifId + ") : " + reqResult;
		} else {
			jsonResult = new JSONObject(reqResult);
			if (jsonResult.has(NARAConstants.OIF_ID)) {
				logger.info(
						"The Group with ID(" + jsonResult.getString(NARAConstants.OIF_ID) + ") created successfully");
				finalResults = jsonResult.getString(NARAConstants.OIF_ID);
			} else {
				finalResults = "ERROR in Create Group No oifId : " + reqResult;
			}
		}

		return finalResults;

	}

	private String createItem(JSONObject processFields, String BOType, String oifId, String BOId, int itemId,
			String boDataSource, List<String> inactiveItemsIds, JSONObject userActionData, List<String> cookie) {

		String finalResults = "", boType = "", hRItemId = "";

		JSONObject apiReqBody = getProcessFields(processFields, "create_item", itemId), reqJOData = new JSONObject();

		if (apiReqBody.isEmpty()) {
			return finalResults;
		}

		if (boDataSource.equals("DB")) {
			if (apiReqBody.has(NARAConstants.FORM_DATA)) {
				JSONObject formData = apiReqBody.getJSONObject(NARAConstants.FORM_DATA);

				if (formData.has("hRItemId")) {
					hRItemId = formData.getString("hRItemId");
					apiReqBody.put("hRItemId", hRItemId);
					apiReqBody.getJSONObject(NARAConstants.FORM_DATA).remove("hRItemId");
				}
			}
		}

		if (apiReqBody.has("hRItemId") && !apiReqBody.getString("hRItemId").isEmpty()) {

			apiReqBody.put("childId", apiReqBody.getString("hRItemId"));
			apiReqBody.remove("hRItemId");

		} else if (!BOId.isEmpty())
			return "ERROR in Create_Item No HumanReadableID (hRItemId) for the item id(" + itemId + ")";

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("The item with id (" + itemId + ") doesn't exist in user action history list");

		if (!reqJOData.isEmpty()) {
			if (apiReqBody.has("childId") && !apiReqBody.getString("childId").isEmpty())
				apiReqBody.put("childId", apiReqBody.getString("childId") + "#" + reqJOData.toString());
			else
				apiReqBody.put("childId", reqJOData.toString());
		}

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			boType = NARAConstants.BO_TYPES_MAP.get(BOType);
		else {
			logger.error("ERROR in createItem this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in createItem this BO (" + oifId + ") type (" + BOType + ") is not found";
		}
		apiReqBody.put("boType", boType);

		List<String> sectionList = dBUtil.getSectionsByAction(boType, "create_item");

		JSONObject jsonResult = null, formData = apiReqBody.getJSONObject(NARAConstants.FORM_DATA), temp;

		List<String> fieldList;

		String itemOifId = "";

		for (String sec : sectionList) {

			logger.info("The section (" + sec + ")");

			temp = new JSONObject();

			fieldList = dBUtil.getFieldsBySection(boType, "create_item", sec);

			for (String fieldId : fieldList)
				if (formData.has(fieldId))
					temp.put(fieldId, formData.get(fieldId));

			if (temp.isEmpty())
				continue;

			apiReqBody.put("view", sec);
			apiReqBody.put(NARAConstants.FORM_DATA, temp);

			if (itemOifId.isEmpty())
				apiReqBody.put("updateType", "CREATE_ITEM");
			else {
				apiReqBody.put("updateType", "EDIT_ITEM");

				if (reqJOData.isEmpty())
					apiReqBody.put("childId", itemOifId);
				else
					apiReqBody.put("childId", itemOifId + "#" + reqJOData.toString());

			}

			String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
					apiReqBody.toString(), cookie);

			if (reqResult.startsWith("ERROR")) {

				logger.error("ERROR in Create_Item (" + oifId + "): " + reqResult);
				finalResults = "ERROR in Create_Item (" + oifId + "): " + reqResult;

			} else {
				jsonResult = new JSONObject(reqResult);
				if (jsonResult.has(NARAConstants.OIF_ID)) {
					logger.info("The Item with ID(" + jsonResult.getString(NARAConstants.OIF_ID)
							+ ") created successfully");
					finalResults = jsonResult.getString(NARAConstants.OIF_ID);
				} else {
					finalResults = "ERROR in Create_Item No Item_ID (oifId) :" + reqResult;
				}

			}

			if (finalResults.startsWith(NARAConstants.ERROR))
				break;
			else {
				if (itemOifId.isEmpty())
					itemOifId = finalResults;
			}

		}

		if (!hRItemId.isEmpty()) {
			String itemStatus = itemStatusRepo.getItemStatusByBOIDAndItemId(oifId, hRItemId);
			if (itemStatus != null && itemStatus.toLowerCase().equals("inactive"))
				inactiveItemsIds.add(String.valueOf(hRItemId));
		}

		if (finalResults.startsWith(NARAConstants.ERROR))
			return finalResults;
		else
			return itemOifId;

	}

	private String addReviewersToBO(String BOType, String oifId, JSONArray attArray, List<String> cookie) {

		String finalResults = "";
		JSONObject reviewers = new JSONObject();
		JSONObject processFieldsMap = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addReviewersToBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addReviewersToBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		processFieldsMap.put("updateType", "EDIT");
		processFieldsMap.put("view", "AdditionalReviewers");
		processFieldsMap.put(NARAConstants.ACTION, JSONObject.NULL);
		processFieldsMap.put("childId", "");
		reviewers.put("L7Adki_v1", attArray);
		processFieldsMap.put(NARAConstants.FORM_DATA, reviewers);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addReviewersToBO (" + oifId + "): " + reqResult;
			logger.error("ERROR in addReviewersToBO (" + oifId + "): " + reqResult);

		} else {
			finalResults = "All reviewers have been successfully added to the BO(" + oifId + ")";
			logger.info("All reviewers have been successfully added to the BO(" + oifId + ")");

		}

		return finalResults;

	}

	private String addFederalAttToBO(String BOType, String oifId, JSONArray attArray, JSONArray userActionHistoryList,
			List<String> cookie) {

		String finalResults = "";
		JSONObject attachments = new JSONObject();

		JSONObject processFieldsMap = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addFederalAttToBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addFederalAttToBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		JSONObject userActionData = getUserActionHisByActionName(userActionHistoryList, "FederalAttachments"),
				reqJOData = new JSONObject();

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("Federal Attachments doesn't exist in user action history list");

		if (!reqJOData.isEmpty())
			processFieldsMap.put("childId", reqJOData.toString());
		else
			processFieldsMap.put("childId", "");

		processFieldsMap.put("view", "AppraisalMemo");
		processFieldsMap.put(NARAConstants.ACTION, JSONObject.NULL);
		attachments.put("Mjl5Ei_v1", attArray);
		processFieldsMap.put(NARAConstants.FORM_DATA, attachments);

		processFieldsMap.put("updateType", "APPRAISAL_EDIT");
		System.out.println("processFieldsMap: " + processFieldsMap);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addFederalAttToBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in addFederalAttToBO (" + oifId + ") : " + reqResult);
		} else {
			finalResults = reqResult;
			logger.info("Federal attachment have been successfully added to the BO(" + oifId + ")");

		}

		return finalResults;

	}

	//
	private String addFederalDocToBO(String BOType, String oifId, JSONArray fedDocArray, JSONArray attArray,
			JSONArray userActionHistoryList, List<String> cookie) {

		String finalResults = "";
		JSONObject attachments = new JSONObject();

		JSONObject processFieldsMap = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addFederalDocToBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addFederalDocToBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		JSONObject userActionData = getUserActionHisByActionName(userActionHistoryList, "FederalDocuments"),
				reqJOData = new JSONObject();

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("Federal Documents doesn't exist in user action history list");

		if (!reqJOData.isEmpty())
			processFieldsMap.put("childId", reqJOData.toString());
		else
			processFieldsMap.put("childId", "");

		attachments.put("IObXwo_v1", bOFieldsRepo.getFieldValueByFieldKeyAndBOID(oifId, "IObXwo_v1").toString());
		attachments.put("Mjl5Ei_v1", attArray);
		attachments.put("waJs9o_v1", fedDocArray);
		processFieldsMap.put(NARAConstants.FORM_DATA, attachments);

		processFieldsMap.put("updateType", "EDIT");
		processFieldsMap.put("view", "GeneralFederalRegister");
		processFieldsMap.put(NARAConstants.ACTION, "APPROVE_FEDERAL_REGISTER_DOCUMENT");

		System.out.println("processFieldsMap: " + processFieldsMap);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addFederalDocToBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in addFederalDocToBO (" + oifId + ") : " + reqResult);
		} else {
			finalResults = reqResult;
			logger.info("Federal Document have been successfully added to the BO(" + oifId + ")");
		}

		return finalResults;

	}

	private String addAttToBO(String BOType, String oifId, JSONArray attArray, JSONArray userActionHistoryList,
			List<String> cookie) {

		String finalResults = "";
		JSONObject attachments = new JSONObject();

		JSONObject processFieldsMap = new JSONObject();

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addAttToBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addAttToBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		JSONObject userActionData = getUserActionHisByActionName(userActionHistoryList, "Attachments"),
				reqJOData = new JSONObject();

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());
		else
			logger.info("Attachments doesn't exist in user action history list");

		if (!reqJOData.isEmpty())
			processFieldsMap.put("childId", reqJOData.toString());
		else
			processFieldsMap.put("childId", "");

		processFieldsMap.put("view", "Attachment");
		processFieldsMap.put(NARAConstants.ACTION, JSONObject.NULL);
		attachments.put("Zs1M0q_v1", attArray);
		processFieldsMap.put(NARAConstants.FORM_DATA, attachments);

		processFieldsMap.put("updateType", "UPDATE_ATT");
		System.out.println("processFieldsMap: " + processFieldsMap);

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addAttToBO (" + oifId + ") : " + reqResult;
			logger.error("ERROR in addAttToBO (" + oifId + ") : " + reqResult);
		} else {
			finalResults = "All attachments have been successfully added to the BO(" + oifId + ")";
			logger.info("All attachments have been successfully added to the BO(" + oifId + ")");

		}

		return finalResults;

	}

	private String addContactToBO(String BOType, String oifId, JSONObject apiReqBody, List<String> cookie,
			String boDataSource) {

		String finalResults = "", userId = "", url = "";

		if (apiReqBody.isEmpty() || !apiReqBody.has(NARAConstants.FORM_DATA)) {
			return "ERROR in addContactToBO : The contact info is missing";
		}

		if (boDataSource.equals("DB")) {
			if (apiReqBody.has(NARAConstants.FORM_DATA)) {
				JSONObject formData = apiReqBody.getJSONObject(NARAConstants.FORM_DATA);
				if (formData.has(NARAConstants.USER_ID)) {
					apiReqBody.put(NARAConstants.USER_ID, formData.getString(NARAConstants.USER_ID));
					apiReqBody.getJSONObject(NARAConstants.FORM_DATA).remove(NARAConstants.USER_ID);
				}

			}
		}

		if (apiReqBody.has(NARAConstants.USER_ID)) {

			userId = apiReqBody.getString(NARAConstants.USER_ID);
			if (userId.isEmpty()) {
				return "ERROR in addContactToBO : The userId field is missing";
			}
			apiReqBody.remove(NARAConstants.USER_ID);
		}

		switch (BOType) {

			case "recordSchedule":
				if (userId.isEmpty())
					url = "views/RecordsSchedule/bos/" + oifId + "/contact/add";
				else
					url = "views/RecordsSchedule/bos/" + oifId + "/contact/add/" + userId;
				break;

			case "transferRequest":
				if (userId.isEmpty())
					url = "views/TransferRequest/bos/" + oifId + "/contact/add";
				else
					url = "views/TransferRequest/bos/" + oifId + "/contact/add/" + userId;
				break;
		}

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + url,
				apiReqBody.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addContactToBO (" + oifId + "): " + reqResult;
			logger.error("ERROR in addContactToBO (" + oifId + "): " + reqResult);
		} else {
			finalResults = "The contact info has been successfully added to the BO(" + oifId + ")";
			logger.info("The contact info has been successfully added to the BO(" + oifId + ")");
		}

		return finalResults;

	}

	private String addItemToBO(String BOType, String oifId, String itemID, JSONObject userActionData,
			List<String> cookie) {

		String finalResults = "";

		JSONObject processFieldsMap = new JSONObject(), reqJOData = new JSONObject();

		if (userActionData != null)
			reqJOData.put(NARAConstants.USER_ACTION_DATA, updateUserActionInfo(userActionData).toString());

		processFieldsMap.put("updateType", "EDIT_ITEM");

		if (NARAConstants.BO_TYPES_MAP.containsKey(BOType))
			processFieldsMap.put("boType", NARAConstants.BO_TYPES_MAP.get(BOType));
		else {
			logger.error("ERROR in addItemToBO this BO (" + oifId + ") type (" + BOType + ") is not found");
			return "ERROR in addItemToBO this BO (" + oifId + ") type (" + BOType + ") is not found";
		}

		processFieldsMap.put("view", "BOItemView");
		processFieldsMap.put(NARAConstants.ACTION, "ADD_ITEM");
		processFieldsMap.put(NARAConstants.FORM_DATA, new JSONObject());

		if (reqJOData.isEmpty())
			processFieldsMap.put("childId", itemID);
		else
			processFieldsMap.put("childId", itemID + "#" + reqJOData.toString());

		String reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
				+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/" + oifId,
				processFieldsMap.toString(), cookie);

		if (reqResult.startsWith("ERROR")) {
			finalResults = "ERROR in addItemToBO (" + oifId + "): " + reqResult;
			logger.error("ERROR in addItemToBO (" + oifId + "): " + reqResult);
		} else {
			finalResults = "The item(" + itemID + ") has been successfully added to the BO(" + oifId + ")";
			logger.info("The item(" + itemID + ") has been successfully added to the BO(" + oifId + ")");
		}

		return finalResults;

	}

	private String getItemsByBoIdFromNaraApi(String oifId) {

		String finalResults = "", result = "";

		String inactivateItemUser = boTaskRepo.getUserNameByBOandTask(oifId, "INACTIVE_ITEMS");

		List<String> cookie = apiUtil.getCookies(inactivateItemUser);

		if (cookie.isEmpty()) {

			logger.error("ERROR: Login for getItemsByBoIdFromNaraApi for the BO: (" + oifId
					+ ") is failed for this user: " + inactivateItemUser);

			dBUtil.updateStatus(oifId, -1, "Login for getItemsByBoIdFromNaraApi for the BO: (" + oifId
					+ ") is failed for this user: " + inactivateItemUser, "getItemsByBoIdFromNaraApi");
			return "ERROR: Login for getItemsByBoIdFromNaraApi item for the BO: (" + oifId
					+ ") is failed for this user: " + inactivateItemUser;
		}
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet httpGet;
		CloseableHttpResponse response;
		int statusCode;

		try {

			httpGet = new HttpGet(
					env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
							+ "v1/business-objects/" + oifId + "/item-group-tree/false");

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
				finalResults = "ERROR in getItemsByBoIdFromNaraApi for BO (" + oifId + ")  with code(" + statusCode
						+ "): " + result;
				logger.error("ERROR in getItemsByBoIdFromNaraApi for BO (" + oifId + ")  with code(" + statusCode
						+ "): " + result);
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

	private String inactivateItems(String oifId, Map<String, String> itemsUuidsMap, List<String> inactiveItemsIds,
			JSONObject processFields) {

		String finalResults = "", reqResult;

		String inactivateItemUser = boTaskRepo.getUserNameByBOandTask(oifId, "INACTIVE_ITEMS");

		List<String> cookie = apiUtil.getCookies(inactivateItemUser);

		if (cookie.isEmpty()) {

			logger.error("ERROR: Login for inactivate item for the BO: (" + oifId + ") is failed for this user: "
					+ inactivateItemUser);

			dBUtil.updateStatus(oifId, -1, "Login for inactivate item for the BO: (" + oifId
					+ ") is failed for this user: " + inactivateItemUser, "inactivateItem");
			return "ERROR: Login for inactivate item for the BO: (" + oifId + ") is failed for this user: "
					+ inactivateItemUser;
		}

		String inactiveReason;
		JSONObject apiData;

		JSONObject reqBody, formDataJson;

		for (String itemId : inactiveItemsIds) {

			inactiveReason = " ";

			apiData = getProcessFields(processFields, "Inactive_Item",
					Integer.parseInt(bOFieldsRepo.getSeqIdByItemId(itemId).toString()));
			if (apiData.has(NARAConstants.FORM_DATA))
				if (apiData.getJSONObject(NARAConstants.FORM_DATA).has("NTM8pL_v1"))
					inactiveReason = apiData.getJSONObject(NARAConstants.FORM_DATA).getString("NTM8pL_v1");

			formDataJson = new JSONObject();
			formDataJson.put("n46Hfr_v1", oifId);
			formDataJson.put("mYflpJ_v1", itemsUuidsMap.get(itemId));
			formDataJson.put("NTM8pL_v1", "Inactive Date: " + inactiveReason);

			reqBody = new JSONObject().put(NARAConstants.FORM_DATA, formDataJson);

			reqResult = httpPathch(env.getProperty("nara.era2.serverURL")
					+ env.getProperty("nara.era2.servicesFormWorkflow") + "v1/business-objects/inactivateItem",
					reqBody.toString(), cookie);

			if (reqResult.startsWith("ERROR")) {

				finalResults = "ERROR in inactivateItems (" + itemsUuidsMap.get(itemId) + ") in BO (" + oifId + "): "
						+ reqResult;
				logger.error("ERROR in inactivateItems (" + itemsUuidsMap.get(itemId) + ") in BO (" + oifId + ") : "
						+ reqResult);
				break;

			} else {
				logger.info(
						"The status of item (" + itemsUuidsMap.get(itemId) + ") in the BO (" + oifId + ") is inactive");
			}

		}

		if (!finalResults.startsWith("ERROR"))
			return "All items in the BO (" + oifId + ") have been converted inactive successfully.";

		return finalResults;

	}

	private String submitBO(String BOType, String oifId, JSONObject taskJO, JSONArray userActionHistoryList,
			List<String> cookie, boolean hasInactiveItem) {

		String action = taskJO.getString(NARAConstants.KEY);

		JSONObject processFieldsMap = new JSONObject(), userActionData, actionComments;

		switch (BOType) {

			case "recordSchedule":
				processFieldsMap.put("boType", NARAConstants.RECORDS_SCHEDULE);
				processFieldsMap.put(NARAConstants.FORM_DATA, new JSONObject());
				processFieldsMap.put("updateType", "ACTION");
				processFieldsMap.put("view", "RecordsSchedule");
				processFieldsMap.put(NARAConstants.ACTION, action);
				break;

			case "transferRequest":
				processFieldsMap.put("boType", NARAConstants.TRANSFER_REQUEST);
				processFieldsMap.put("updateType", "ACTION");
				processFieldsMap.put("view", "TransferRequest");
				processFieldsMap.put(NARAConstants.ACTION, action);
				if (!taskJO.getString(NARAConstants.COMMENTS).isEmpty()) {
					actionComments = new JSONObject();
					if (action.equals("PROPOSE"))
						actionComments.put("hq5GPe_v1", taskJO.getString(NARAConstants.COMMENTS));
					if (action.equals("APPROVE"))
						actionComments.put("BD5fkg_v1", taskJO.getString(NARAConstants.COMMENTS));
					processFieldsMap.put(NARAConstants.FORM_DATA, actionComments);
				} else
					processFieldsMap.put(NARAConstants.FORM_DATA, new JSONObject());
				break;

			case "DAL":
				processFieldsMap.put("boType", NARAConstants.DAL);
				processFieldsMap.put(NARAConstants.FORM_DATA, new JSONObject());
				processFieldsMap.put("updateType", "ACTION");
				processFieldsMap.put("view", NARAConstants.DAL);
				processFieldsMap.put(NARAConstants.ACTION, action);
				break;

			case "NA_1005":
				processFieldsMap.put("boType", NARAConstants.NA_1005);
				processFieldsMap.put(NARAConstants.FORM_DATA, new JSONObject());
				processFieldsMap.put("updateType", "ACTION");
				processFieldsMap.put("view", "NA1005Form");
				processFieldsMap.put(NARAConstants.ACTION, action);
				break;
		}

		userActionData = getUserActionHisByActionName(userActionHistoryList, action);
		if (userActionData != null && !userActionData.isEmpty()) {
			processFieldsMap.put("childId", updateUserActionInfo(userActionData).toString());
		} else
			logger.info("This action (" + action + ") doesn't exist in user action history list");

		return httpPathch(env.getProperty("nara.era2.serverURL") + env.getProperty("nara.era2.servicesFormWorkflow")
				+ "v1/business-objects/" + oifId, processFieldsMap.toString(), cookie);

	}

	private JSONObject getProcessFields(JSONObject processFields, String action, int countId) {

		JSONObject processFieldsMap = new JSONObject(), actionInfo, Jtemp;
		String field_name = "", field_key = "", field_value = "", id = "";

		if (!processFields.has(action)) {
			return processFieldsMap;
		}

		JSONArray actionFields = processFields.getJSONArray(action);

		for (Object object : actionFields) {
			actionInfo = (JSONObject) object;

			if (actionInfo.has(NARAConstants.FIELD_NAME))
				field_name = actionInfo.getString(NARAConstants.FIELD_NAME);
			if (actionInfo.has(NARAConstants.FIELD_KEY))
				field_key = actionInfo.getString(NARAConstants.FIELD_KEY);
			if (actionInfo.has(NARAConstants.FIELD_VALUE))
				field_value = actionInfo.getString(NARAConstants.FIELD_VALUE);

			if (actionInfo.has(NARAConstants.ID))
				id = actionInfo.getString(NARAConstants.ID);

			if (action.contains("_") && !id.equals(String.valueOf(countId)))
				continue;

			if (field_name.equals(NARAConstants.FORM_DATA)) {

				if (processFieldsMap.has(NARAConstants.FORM_DATA))
					Jtemp = processFieldsMap.getJSONObject(NARAConstants.FORM_DATA);
				else
					Jtemp = new JSONObject();

				if (actionInfo.has(NARAConstants.FIELD_KEY)) {
					try {
						if (field_value.startsWith("["))
							Jtemp.put(field_key, new JSONArray(field_value));
						else if (field_value.startsWith("{"))
							Jtemp.put(field_key, new JSONObject(field_value));
						else
							Jtemp.put(field_key, field_value);
					} catch (JSONException err) {
						logger.error("Error: This field (" + field_key + ") has an incorrect value: (" + field_value
								+ ") Error: " + err.toString());
						Jtemp.put(field_key, field_value);
					}
				}

				processFieldsMap.put(NARAConstants.FORM_DATA, Jtemp);

			} else
				processFieldsMap.put(field_name, field_value);

		}
		return processFieldsMap;

	}

	private int getMaxSeqIdByAction(String oifId, String action) {

		Integer maxSeq = bOFieldsRepo.getMaxSeqIdByAction(oifId, action);
		if (maxSeq != null)
			return maxSeq.intValue();
		return 0;

	}

	private String httpPathch(String url, String entityString, List<String> cookie) {

		CloseableHttpClient client = HttpClients.createDefault();
		String finalResults;
		try {

			HttpPatch httpPatch = new HttpPatch(url);

			StringEntity entity = new StringEntity(entityString, "UTF-8");
			httpPatch.setEntity(entity);
			httpPatch.setHeader("Accept", "application/json");
			httpPatch.setHeader("Content-type", "application/json");

			for (String s : cookie) {
				httpPatch.addHeader("Cookie", s);
			}

			httpPatch.addHeader("t", String.valueOf(System.currentTimeMillis()));

			logger.info("httpPatch: " + httpPatch.toString());

			CloseableHttpResponse response = client.execute(httpPatch);

			String result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {

				logger.info("SUCCESS in httpPatch: " + url);
				finalResults = result;

			} else {
				finalResults = "ERROR in httpPatch with code(" + statusCode + "): " + result;
				logger.error("ERROR in httpPatch (" + url + ") with code (" + statusCode + ") \nThe body: "
						+ entityString + " \nThe response: " + result);
			}

		} catch (IOException ex) {
			finalResults = "ERROR in httpPatch (" + url + "):" + ex.getMessage();
			logger.error("ERROR in httpPatch (" + url + "):" + ex.getMessage());

		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				finalResults = "ERROR in httpPatch (" + url + "):" + ex.getMessage();
				logger.error("ERROR in httpPatch (" + url + "):" + ex.getMessage());
			}
		}
		return finalResults;
	}

}