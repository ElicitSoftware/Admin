package com.elicitsoftware.admin.upload;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;

import java.io.InputStream;

public class MultipartBody {

    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream file;

    @FormParam("fileName")
    @PartType(MediaType.TEXT_PLAIN)
    public String fileName;

    @FormParam("username")
    @PartType(MediaType.TEXT_PLAIN)
    public String username;

    //    using String not long because of this bug.
//    https://github.com/quarkusio/quarkus/issues/8239
    @FormParam("surveyId")
    @PartType(MediaType.TEXT_PLAIN)
    public String surveyId;

    //    using String not long because of this bug.
//    https://github.com/quarkusio/quarkus/issues/8239
    @FormParam("departmentId")
    @PartType(MediaType.TEXT_PLAIN)
    public String departmentId;
}
