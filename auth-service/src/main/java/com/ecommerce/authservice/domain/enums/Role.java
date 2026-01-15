package com.ecommerce.authservice.domain.enums;

public enum Role {
    GUEST("ROLE_GUEST"),
    CUSTOMER("ROLE_CUSTOMER"),
    ADMIN("ROLE_ADMIN");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }
}
