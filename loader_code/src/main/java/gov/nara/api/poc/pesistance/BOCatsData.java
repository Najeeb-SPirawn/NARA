package gov.nara.api.poc.pesistance;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import gov.nara.api.poc.constants.NARAConstants;

@Entity
@Table(name = "ctrl_cats_data", schema = NARAConstants.SCHEMA_NAME)
public class BOCatsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "dal_num")
    private String boId;

    @Column(name = "date_recd")
    private String acceptDate;

    @Column(name = "inactive_dt")
    private String inactiveDate;
    
}
