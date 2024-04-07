package com.yupi.rrapicommon.service;


import com.yupi.rrapicommon.model.entity.User;

/**
 * 用户服务
 *
 * @author yupi
 */
public interface InnerUserService {

    /**
     * 数据库中是否已分配给用户秘钥.
     * @param accessKey
     * @return
     */
    User getInvokeUser(String accessKey);


}
