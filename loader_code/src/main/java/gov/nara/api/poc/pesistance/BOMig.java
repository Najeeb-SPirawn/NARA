package gov.nara.api.poc.pesistance;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import gov.nara.api.poc.constants.NARAConstants;

@Entity
@Table(name = "stage_bo_migration", schema = NARAConstants.SCHEMA_NAME)
public class BOMig {

	@Id
	@Column(name = "bo_id")
	private String boId;

	@Column(name = "bo_type")
	private String boType;

	@Column(name = "mig_status")
	private Integer migStatus;

	@Column(name = "mig_last_action")
	private String lastAction;

	@Column(name = "bo_xml_file")
	private String boXmlFile;

	@Column(name = "mig_error", columnDefinition = "TEXT")
	private String migError;

	@Column(name = "created_date")
	private Date createdDate;

	@Column(name = "updated_date")
	private Date lastModifiedDate;

	public String getBoId() {
		return boId;
	}

	public void setBoId(String boId) {
		this.boId = boId;
	}

	public Integer getMigStatus() {
		return migStatus;
	}

	public void setMigStatus(Integer migStatus) {
		this.migStatus = migStatus;
	}

	public String getMigError() {
		return migError;
	}

	public void setMigError(String migError) {
		this.migError = migError;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	public Date getLastModifiedDate() {
		return lastModifiedDate;
	}

	public void setLastModifiedDate(Date lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}

	public String getBoType() {
		return boType;
	}

	public void setBoType(String boType) {
		this.boType = boType;
	}

	public String getBoXmlFile() {
		return boXmlFile;
	}

	public void setBoXmlFile(String boXmlFile) {
		this.boXmlFile = boXmlFile;
	}

	public String getLastAction() {
		return lastAction;
	}

	public void setLastAction(String lastAction) {
		this.lastAction = lastAction;
	}

	@Override
	public String toString() {

		return "BOStates [ID=" + boId + ",TYPE=" + boType + ",STATUS=" + migStatus + ",ERROR=" + migError
				+ ",CREATED=" + createdDate + ",UPDATED=" + lastModifiedDate + "]";
	}

}
