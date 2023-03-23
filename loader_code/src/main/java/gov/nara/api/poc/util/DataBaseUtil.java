package gov.nara.api.poc.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import gov.nara.api.poc.pesistance.BOMig;
import gov.nara.api.poc.pesistance.BOMigRepo;
import gov.nara.api.poc.pesistance.BOSections;
import gov.nara.api.poc.pesistance.BOSectionsRepo;
import gov.nara.api.poc.pesistance.FieldDefs;
import gov.nara.api.poc.pesistance.FieldDefsRepo;

@Component
public class DataBaseUtil {

    @Autowired
    private BOMigRepo bOMigRepo;

    @Autowired
    private FieldDefsRepo fieldDefRepo;

    @Autowired
    private BOSectionsRepo sectionRepo;

    public List<String> getSectionsByAction(String type, String action) {

        List<BOSections> sectionList = sectionRepo.selectSectionsByTypeAndAction(type, action);
        List<String> sections = new ArrayList<String>();
        for (BOSections sec : sectionList)
            sections.add(sec.getSection());
        return sections;

    }

    public List<String> getFieldsBySection(String type, String action, String sec) {

        List<BOSections> sectionList = sectionRepo.selectFieldsByTypeAndActionAndSections(type, action, sec);
        List<String> fields = new ArrayList<String>();
        String fieldsStr = "";
        if (!sectionList.isEmpty())
            fieldsStr = sectionList.get(0).getFields();

        if (fieldsStr.isEmpty())
            return fields;

        String[] fieldsStrArr = fieldsStr.split(",");

        for (String field : fieldsStrArr)
            fields.add(field);

        return fields;

    }

    public void createStatus(String boId, int status, String error, String boType, String xmlFilePath,
            String lastAction) {
        BOMig bo = new BOMig();
        bo.setBoId(boId);
        bo.setMigStatus(status);
        bo.setMigError(error);
        bo.setBoXmlFile(xmlFilePath);
        bo.setBoType(boType);
        Date date = new Date();
        bo.setCreatedDate(date);
        bo.setLastModifiedDate(date);
        bo.setLastAction(lastAction);

        bOMigRepo.save(bo);
    }

    public void updateStatus(String boId, int status, String error, String lastAction) {

        Optional<BOMig> boR = bOMigRepo.findById(boId);
        if (boR.isPresent()) {
            BOMig bo = boR.get();
            bo.setMigStatus(status);
            bo.setLastAction(lastAction);
            bo.setMigError(error);
            bo.setLastModifiedDate(new Date());
            bOMigRepo.save(bo);
        }

    }

    public void updateBOId(String oldBoId, String newBoId) {

        Optional<BOMig> boR = bOMigRepo.findById(oldBoId);
        if (boR.isPresent()) {
            BOMig bo = boR.get();
            bo.setBoId(newBoId);
            bOMigRepo.save(bo);
            bOMigRepo.deleteById(oldBoId);
        }
    }

    public int checkBOid(String boId) {

        Optional<BOMig> boR = bOMigRepo.findById(boId);

        if (boR.isPresent()) {
            BOMig bo = boR.get();
            return bo.getMigStatus();
        }
        return 0;

    }

    public String validateFields(String fieldKey, String fieldValue) {

        Optional<FieldDefs> fieldInfo = fieldDefRepo.findById(fieldKey);

        if (fieldInfo.isPresent()) {
            FieldDefs defs = fieldInfo.get();

            if (defs.getIsRequired() != null && defs.getIsRequired() && fieldValue.isEmpty())
                return "The value for this field (" + fieldKey + ") should not be empty";

            if (defs.getLength() != null && (fieldValue.length() > defs.getLength()))
                return "The value length (" + fieldValue.length() + ") for this field (" + fieldKey
                        + ") is bigger than the max length (" + defs.getLength() + ")";

            if (defs.getData() != null && !defs.getData().isEmpty()
                    && !defs.getData().contains("\"" + fieldValue + "\""))
                return "The value (" + fieldValue + ") for this field (" + fieldKey
                        + ") should be one of these values ("
                        + defs.getData() + ")";

        }
        return "";
    }
}