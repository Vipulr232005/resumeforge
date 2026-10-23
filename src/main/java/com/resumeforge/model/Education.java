package com.resumeforge.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Education(
    String degree,
    String field,
    String institution,
    String graduationDate,
    String gpa,
    String notes
) {
}
