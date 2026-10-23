package com.resumeforge.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Contact(
    String fullName,
    String email,
    String phone,
    String location,
    String linkedin,
    String github,
    String portfolio
) {
}
