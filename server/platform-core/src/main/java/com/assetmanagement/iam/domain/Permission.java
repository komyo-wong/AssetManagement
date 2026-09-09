package com.assetmanagement.iam.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 160)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "module", nullable = false, length = 80)
    private String module;

    @Column(name = "description", length = 500)
    private String description;

    protected Permission() {
    }

    public Permission(String code, String name, String module) {
        this.code = code;
        this.name = name;
        this.module = module;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}

