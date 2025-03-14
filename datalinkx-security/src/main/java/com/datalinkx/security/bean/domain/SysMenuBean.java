package com.datalinkx.security.bean.domain;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApiModel(description = "菜单")
@Data
@FieldNameConstants
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@DynamicInsert
@DynamicUpdate
@Entity
@SQLDelete(sql = "update sys_menu set status = 1 where menu_id = ?")
@Table(name = "sys_menu")
public class SysMenuBean {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "menu_id")
    private String menuId;
    @Basic
    @Column(name = "menu_name")
    private String menuName;
    @Basic
    @Column(name = "parent_id")
    private String parentId;
    @Basic
    @Column(name = "order_num")
    private Integer orderNum;
    @Basic
    @Column(name = "path")
    private String path;
    @Basic
    @Column(name = "component")
    private String component;
    @Basic
    @Column(name = "query")
    private String query;
    @Basic
    @Column(name = "route_name")
    private String routeName;
    @Basic
    @Column(name = "is_frame")
    private String isFrame;
    @Basic
    @Column(name = "is_cache")
    private String isCache;
    @Basic
    @Column(name = "menu_type")
    private String menuType;
    @Basic
    @Column(name = "visible")
    private String visible;
    @Basic
    @Column(name = "status")
    private String status;
    @Basic
    @Column(name = "perms")
    private String perms;
    @Basic
    @Column(name = "icon")
    private String icon;
    @Basic
    @Column(name = "create_by")
    private String createBy;
    @Basic
    @Column(name = "create_time")
    private Timestamp createTime;
    @Basic
    @Column(name = "update_by")
    private String updateBy;
    @Basic
    @Column(name = "update_time")
    private Timestamp updateTime;
    @Basic
    @Column(name = "remark")
    private String remark;
    @Transient
    private List<SysMenuBean> children = new ArrayList<>();

    public String getMenuId() {
        return menuId;
    }

    public void setMenuId(String menuId) {
        this.menuId = menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getIsFrame() {
        return isFrame;
    }

    public void setIsFrame(String isFrame) {
        this.isFrame = isFrame;
    }

    public String getIsCache() {
        return isCache;
    }

    public void setIsCache(String isCache) {
        this.isCache = isCache;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getVisible() {
        return visible;
    }

    public void setVisible(String visible) {
        this.visible = visible;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPerms() {
        return perms;
    }

    public void setPerms(String perms) {
        this.perms = perms;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Timestamp getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysMenuBean that = (SysMenuBean) o;
        return menuId == that.menuId && Objects.equals(menuName, that.menuName) && Objects.equals(parentId, that.parentId) && Objects.equals(orderNum, that.orderNum) && Objects.equals(path, that.path) && Objects.equals(component, that.component) && Objects.equals(query, that.query) && Objects.equals(routeName, that.routeName) && Objects.equals(isFrame, that.isFrame) && Objects.equals(isCache, that.isCache) && Objects.equals(menuType, that.menuType) && Objects.equals(visible, that.visible) && Objects.equals(status, that.status) && Objects.equals(perms, that.perms) && Objects.equals(icon, that.icon) && Objects.equals(createBy, that.createBy) && Objects.equals(createTime, that.createTime) && Objects.equals(updateBy, that.updateBy) && Objects.equals(updateTime, that.updateTime) && Objects.equals(remark, that.remark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuId, menuName, parentId, orderNum, path, component, query, routeName, isFrame, isCache, menuType, visible, status, perms, icon, createBy, createTime, updateBy, updateTime, remark);
    }
}
