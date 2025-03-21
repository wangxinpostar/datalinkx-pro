package com.datalinkx.security.repository;

import com.datalinkx.security.bean.domain.SysMenuBean;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface SysMenuRepository extends JpaRepository<SysMenuBean, String> {
    /**
     * 查询菜单
     *
     * @return 菜单列表
     */
    @Query(value = "SELECT DISTINCT m.*" +
            "FROM sys_menu m " +
            "WHERE m.menu_type IN ('M', 'C') AND m.status = 0 " +
            "ORDER BY m.parent_id, m.order_num",
            nativeQuery = true)
    List<SysMenuBean> selectMenuTreeAll();

    /**
     * 根据用户ID查询菜单
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Query(value = "SELECT DISTINCT m.*" +
            "FROM sys_menu m " +
            "LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "LEFT JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "LEFT JOIN sys_role ro ON ur.role_id = ro.role_id " +
            "LEFT JOIN sys_user u ON ur.user_id = u.user_id " +
            "WHERE u.user_id = :userId AND m.menu_type IN ('M', 'C') AND m.status = 0 AND ro.status = 0 " +
            "ORDER BY m.parent_id, m.order_num",
            nativeQuery = true)
    List<SysMenuBean> selectMenuTreeByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Query(value = "SELECT DISTINCT m.perms " +
            "FROM sys_menu m " +
            "LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "LEFT JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "LEFT JOIN sys_role r ON r.role_id = ur.role_id " +
            "WHERE m.status = '0' AND r.status = '0' AND ur.user_id = :userId",
            nativeQuery = true)
    List<String> selectMenuPermsByUserId(@Param("userId") String userId);

    /**
     * 根据角色ID查询菜单
     *
     * @param roleId 角色ID
     * @return 菜单列表
     */
    @Query(value = "select distinct m.perms " +
            "from sys_menu m " +
            "LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "WHERE m.status = '0' AND rm.role_id = :roleId",
            nativeQuery = true)
    List<String> selectMenuPermsByRoleId(String roleId);

    /**
     * 根据角色ID查询菜单
     *
     * @param roleId 角色ID
     * @return 菜单列表
     */
    @Query(value = "select distinct m.* " +
            "from sys_menu m " +
            "LEFT JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "WHERE m.status = '0' AND rm.role_id = :roleId",
            nativeQuery = true)
    List<SysMenuBean> selectMenuListByRoleId(String roleId);

    /**
     * 批量删除菜单权限
     * @param menuIds
     * @return
     */
    @Modifying
    @Transactional
    @Query(value = "delete from sys_menu where menu_id in (:menuIds)", nativeQuery = true)
    int deleteByIds(String[] menuIds);
}
