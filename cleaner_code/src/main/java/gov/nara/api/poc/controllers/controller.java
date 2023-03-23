
package gov.nara.api.poc.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import gov.nara.api.poc.services.Imp.DeleteAPSServiceImp;
import gov.nara.api.poc.util.ApiUtil;

@RestController
public class controller {

	@Autowired
	private ApiUtil apiUtil;

	@Autowired
	private DeleteAPSServiceImp deleteAPSServiceImp;

	@RequestMapping(value = "/delete_process", method = RequestMethod.GET)
	public ResponseEntity<?> delete_process() {
		return deleteAPSServiceImp.deleteAllProcess();
	}

	@RequestMapping(value = "/delete_process_by_id", method = RequestMethod.POST)
	public ResponseEntity<?> delete_bo_form_aps_by_id(@RequestBody Map<String, Object> body) {
		apiUtil.clearCookies();

		return deleteAPSServiceImp.delete_bo_from_aps_by_id(body);
	}

}