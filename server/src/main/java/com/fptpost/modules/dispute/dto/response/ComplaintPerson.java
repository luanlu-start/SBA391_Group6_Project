package com.fptpost.modules.dispute.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/** Một bên hoặc nhân sự trong hồ sơ; email chỉ được điền khi người xem là kiểm duyệt viên hoặc quản trị viên. */
public record ComplaintPerson(@JsonIgnore Long id, UUID publicId, String fullName, String email) {

    public ComplaintPerson withoutEmail() {
        return new ComplaintPerson(id, publicId, fullName, null);
    }
}
