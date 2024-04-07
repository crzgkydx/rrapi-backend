package com.yupi.rrapicommon.service;


import com.yupi.rrapicommon.model.entity.InterfaceInfo;

/**
*
*/
public interface InnerInterfaceInfoService {


    /**
     * 从数据库中查询模拟接口是否存在
     */
    InterfaceInfo getInterfaceInfo(String path, String method);

}
