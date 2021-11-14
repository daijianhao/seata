/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm.tcc.remoting.parser;

import io.seata.common.exception.FrameworkException;
import io.seata.common.util.ReflectionUtil;
import io.seata.rm.tcc.remoting.Protocols;
import io.seata.rm.tcc.remoting.RemotingDesc;

/**
 * dubbo  remoting bean parsing
 *
 * @author zhangsen
 */
public class DubboRemotingParser extends AbstractedRemotingParser {

    /**
     * 判断是否是dubbo的 referenceBean
     */
    @Override
    public boolean isReference(Object bean, String beanName) throws FrameworkException {
        Class<?> c = bean.getClass();
        return "com.alibaba.dubbo.config.spring.ReferenceBean".equals(c.getName())
            || "org.apache.dubbo.config.spring.ReferenceBean".equals(c.getName());
    }

    /**
     * 判断是否是dubbo的ServiceBean
     */
    @Override
    public boolean isService(Object bean, String beanName) throws FrameworkException {
        Class<?> c = bean.getClass();
        return "com.alibaba.dubbo.config.spring.ServiceBean".equals(c.getName())
            || "org.apache.dubbo.config.spring.ServiceBean".equals(c.getName());
    }

    /**
     * 获取 Bean 的描述信息
     */
    @Override
    public RemotingDesc getServiceDesc(Object bean, String beanName) throws FrameworkException {
        //再次检查
        if (!this.isRemoting(bean, beanName)) {
            return null;
        }
        try {
            RemotingDesc serviceBeanDesc = new RemotingDesc();
            //获取Bean实现的接口
            Class<?> interfaceClass = (Class<?>)ReflectionUtil.invokeMethod(bean, "getInterfaceClass");
            String interfaceClassName = (String)ReflectionUtil.getFieldValue(bean, "interfaceName");
            //版本
            String version = (String)ReflectionUtil.invokeMethod(bean, "getVersion");
            //分组
            String group = (String)ReflectionUtil.invokeMethod(bean, "getGroup");
            serviceBeanDesc.setInterfaceClass(interfaceClass);
            serviceBeanDesc.setInterfaceClassName(interfaceClassName);
            serviceBeanDesc.setUniqueId(version);
            serviceBeanDesc.setGroup(group);
            //协议这里是类型 （sofa,dubbo等）
            serviceBeanDesc.setProtocol(Protocols.DUBBO);
            if (isService(bean, beanName)) {
                //如果是ServiceBean,获取真实对象
                Object targetBean = ReflectionUtil.getFieldValue(bean, "ref");
                //设置真实对象
                serviceBeanDesc.setTargetBean(targetBean);
            }
            return serviceBeanDesc;
        } catch (Throwable t) {
            throw new FrameworkException(t);
        }
    }

    @Override
    public short getProtocol() {
        return Protocols.DUBBO;
    }
}
