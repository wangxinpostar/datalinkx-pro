package com.datalinkx.security.controller;

import com.datalinkx.common.result.WebResult;
import com.datalinkx.common.utils.ObjectUtils;
import com.datalinkx.security.bean.domain.SysMenuBean;
import com.datalinkx.security.repository.SysMenuRepository;
import com.datalinkx.security.service.ISysMenuService;
import com.datalinkx.security.utils.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

/**
 * 菜单信息
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/menu")
public class SysMenuController {
    @Autowired
    private ISysMenuService menuService;
    @Autowired
    private SysMenuRepository sysMenuRepository;

    /**
     * 获取菜单列表
     *
     * @return
     * @throws Exception
     */
    @GetMapping("/list")
    public WebResult<HashMap<String, List<SysMenuBean>>> selectMenuList() {
        List<SysMenuBean> menus = null;
        if (SecurityUtils.isAdmin(SecurityUtils.getUserId())) {
            menus = sysMenuRepository.selectMenuTreeAll();
        } else {
            menus = menuService.selectMenuList(SecurityUtils.getUserId());
        }
        HashMap<String, List<SysMenuBean>> resultMap = new HashMap<>();
        resultMap.put("menus", menus);
        return WebResult.of(resultMap);
    }

    /**
     * 更新菜单
     *
     * @param menu
     */
    @PutMapping("/update")
    public WebResult<HashMap<String, Integer>> updateMenu(@RequestBody SysMenuBean menu) {
        HashMap<String, Integer> resultMap = new HashMap<>();
        sysMenuRepository.findById(menu.getMenuId()).ifPresent(sysMenuBean -> {
            BeanUtils.copyProperties(menu, sysMenuBean, ObjectUtils.getNullPropertyNames(menu));
            sysMenuBean.setUpdateBy(SecurityUtils.getUsername());
            sysMenuBean.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            resultMap.put("count", menuService.updateMenu(sysMenuBean));
        });
        return WebResult.of(resultMap);
    }

    /**
     * 新增菜单
     *
     * @param menu
     */
    @PostMapping("/insert")
    public WebResult<HashMap<String, Integer>> insertMenu(@RequestBody SysMenuBean menu) {
        int count = menuService.insertMenu(menu);
        HashMap<String, Integer> resultMap = new HashMap<>();
        resultMap.put("count", count);
        return WebResult.of(resultMap);
    }

    /**
     * 删除菜单
     *
     * @param menuId
     */
    @DeleteMapping("/delete")
    public WebResult<HashMap<String, Integer>> deleteMenu(@RequestBody String[] menuIds) {
        int count = menuService.deleteMenuByIds(menuIds);
        HashMap<String, Integer> resultMap = new HashMap<>();
        resultMap.put("count", count);
        return WebResult.of(resultMap);
    }

}
