package com.datalinkx.security.bean.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuthMenuBody {
    private String roleId;
    private String[] menuIds;

}
