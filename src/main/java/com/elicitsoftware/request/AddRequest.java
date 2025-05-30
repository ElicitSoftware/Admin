package com.elicitsoftware.request;

import jakarta.json.bind.annotation.JsonbDateFormat;

import java.time.LocalDate;

public class AddRequest {
    public int surveyId;
    public String xid;
    public int departmentId;
    public String firstName;
    public String lastName;
    public String middleName;
    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate dob;
    public String email;
    public String phone;
}
