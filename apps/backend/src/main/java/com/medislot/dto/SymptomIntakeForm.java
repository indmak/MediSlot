package com.medislot.dto;

import com.medislot.entity.SeverityLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化症状采集表单。
 */
public class SymptomIntakeForm {

    @Size(max = 500, message = "主诉过长")
    private String chiefComplaint;

    private List<String> symptoms = new ArrayList<>();

    @Size(max = 50, message = "病程过长")
    private String duration;

    private SeverityLevel severity;

    @Size(max = 500, message = "伴随症状过长")
    private String accompanying;

    @Size(max = 500, message = "既往史过长")
    private String pastHistory;

    @Size(max = 500, message = "用药情况过长")
    private String medications;

    @Size(max = 500, message = "过敏史过长")
    private String allergies;

    @DecimalMin(value = "30.0", message = "体温过低")
    @DecimalMax(value = "45.0", message = "体温过高")
    private BigDecimal temperature;

    private List<String> redFlags = new ArrayList<>();

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public List<String> getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(List<String> symptoms) {
        this.symptoms = symptoms == null ? new ArrayList<>() : symptoms;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SeverityLevel severity) {
        this.severity = severity;
    }

    public String getAccompanying() {
        return accompanying;
    }

    public void setAccompanying(String accompanying) {
        this.accompanying = accompanying;
    }

    public String getPastHistory() {
        return pastHistory;
    }

    public void setPastHistory(String pastHistory) {
        this.pastHistory = pastHistory;
    }

    public String getMedications() {
        return medications;
    }

    public void setMedications(String medications) {
        this.medications = medications;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public List<String> getRedFlags() {
        return redFlags;
    }

    public void setRedFlags(List<String> redFlags) {
        this.redFlags = redFlags == null ? new ArrayList<>() : redFlags;
    }
}
