package gov.nara.api.poc.constants;

import java.util.HashMap;
import java.util.Map;

public class NARAConstants {

	public static final String SCHEMA_NAME = "public";
	public static final String OIF_ODS = "oif_ods";

	public static final String RECORDS_SCHEDULE = "RECORDS_SCHEDULE";
	public static final String TRANSFER_REQUEST = "TRANSFER_REQUEST";
	public static final String DAL = "DAL";
	public static final String NA_1005 = "NA_1005";

	public final static String FORM_DATA_USER_ACTION_HISTORY_TIMESTAMP = "WzFr4q_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_ACCEPT_DATE = "WzFr6q_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_INACTIVE_DATE = "WzFr7q_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_USER_NAME = "W6ZBVs_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_USERNAME = "yur2sT_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_USERID = "rMPTEQ_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY_ACTION = "NCLTJm_v1";
	public final static String FORM_DATA_USER_ACTION_HISTORY = "H11Kao_v1";

	public final static String BO_ACTION_CREATEBO = "createBO";
	public final static String BO_ACTION_UPDATEBO = "updateBO";
	public final static String BO_ACTION_SUBMITBO = "submitBO";
	public final static String BO_ACTION_REASSIGNBO = "reassignBO";
	public final static String BO_ACTION_RETURNBO = "returnBO";

	public static final String ID = "id";
	public static final String USER_ACTION_DATA = "userActionData";
	public static final String ERROR = "ERROR";
	public static final String USERNAME = "username";
	public static final String NAME = "name";
	public static final String USER_ID = "userId";
	public static final String FIRST_NAME = "firstName";
	public static final String LAST_NAME = "lastName";
	public static final String TIME_STAMP = "timestamp";
	public static final String ACCEPT_DATE = "acceptDate";

	public static final String KEY = "key";
	public static final String ACTION = "action";
	public static final String TO_USER = "toUser";
	public static final String COMMENTS = "comments";
	public static final String TYPE = "type";

	public static final String FIELD_VALUE = "field-value";
	public static final String FIELD_KEY = "field-key";
	public static final String FIELD_NAME = "field-name";
	public static final String FIELD = "field";

	public static final String OIF_ID = "oifId";

	public final static String FORM_DATA = "formData";
	public final static String HUMAN_READABLE_ID = "humanReadableId";

	public final static String RECORDS_SCHEDULE_NUMBER = "n46Hfr_v1";

	public static final Map<String, String> BO_TYPES_MAP;
	// = Map.of("recordSchedule", RECORDS_SCHEDULE, "transferRequest",
	// TRANSFER_REQUEST, "DAL", DAL, "NA_1005", NA_1005);

	static {
		BO_TYPES_MAP = new HashMap<>();
		BO_TYPES_MAP.put("recordSchedule", "RECORDS_SCHEDULE");
		BO_TYPES_MAP.put("transferRequest", "TRANSFER_REQUEST");
		BO_TYPES_MAP.put("DAL", "DAL");
		BO_TYPES_MAP.put("NA_1005", "NA_1005");
	}
}
