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
package io.seata.spring.util;

import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import io.seata.rm.tcc.remoting.Protocols;
import io.seata.rm.tcc.remoting.RemotingDesc;
import io.seata.rm.tcc.remoting.RemotingParser;
import io.seata.rm.tcc.remoting.parser.DefaultRemotingParser;
import io.seata.spring.tcc.TccActionInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * parser TCC bean
 *
 * @author zhangsen
 */
public class TCCBeanParserUtils {

    private TCCBeanParserUtils() {
    }

    /**
     * is auto proxy TCC bean
     *
     * @param bean               the bean
     * @param beanName           the bean name
     * @param applicationContext the application context
     * @return boolean boolean
     */
    public static boolean isTccAutoProxy(Object bean, String beanName, ApplicationContext applicationContext) {
        //判断bean是否为Remote的bean，如dubbo 的 ReferenceBean(用来引用远程service)
        boolean isRemotingBean = parserRemotingServiceInfo(bean, beanName);
        //get RemotingBean description
        RemotingDesc remotingDesc = DefaultRemotingParser.get().getRemotingBeanDesc(beanName);
        //is remoting bean
        if (isRemotingBean) {
            if (remotingDesc != null && remotingDesc.getProtocol() == Protocols.IN_JVM) {
                //LocalTCC ，本地代理即 in jvm
                return isTccProxyTargetBean(remotingDesc);
            } else {
                //dubbo的referenceBean走这个分支
                // sofa:reference / dubbo:reference, factory bean
                return false;
            }
        } else {
            if (remotingDesc == null) {
                //check FactoryBean
                if (isRemotingFactoryBean(bean, beanName, applicationContext)) {
                    remotingDesc = DefaultRemotingParser.get().getRemotingBeanDesc(beanName);
                    return isTccProxyTargetBean(remotingDesc);
                } else {
                    return false;
                }
            } else {
                return isTccProxyTargetBean(remotingDesc);
            }
        }
    }

    /**
     * if it is proxy bean, check if the FactoryBean is Remoting bean
     *
     * @param bean               the bean
     * @param beanName           the bean name
     * @param applicationContext the application context
     * @return boolean boolean
     */
    protected static boolean isRemotingFactoryBean(Object bean, String beanName,
                                                   ApplicationContext applicationContext) {
        if (!SpringProxyUtils.isProxy(bean)) {
            return false;
        }
        //the FactoryBean of proxy bean
        String factoryBeanName = "&" + beanName;
        Object factoryBean = null;
        if (applicationContext != null && applicationContext.containsBean(factoryBeanName)) {
            factoryBean = applicationContext.getBean(factoryBeanName);
        }
        //not factory bean, needn't proxy
        if (factoryBean == null) {
            return false;
        }
        //get FactoryBean info
        return parserRemotingServiceInfo(factoryBean, beanName);
    }

    /**
     * is TCC proxy-bean/target-bean: LocalTCC , the proxy bean of sofa:reference/dubbo:reference
     *
     * @param remotingDesc the remoting desc
     * @return boolean boolean
     */
    public static boolean isTccProxyTargetBean(RemotingDesc remotingDesc) {
        if (remotingDesc == null) {
            return false;
        }
        //check if it is TCC bean
        boolean isTccClazz = false;
        Class<?> tccInterfaceClazz = remotingDesc.getInterfaceClass();
        Method[] methods = tccInterfaceClazz.getMethods();
        TwoPhaseBusinessAction twoPhaseBusinessAction;
        for (Method method : methods) {
            twoPhaseBusinessAction = method.getAnnotation(TwoPhaseBusinessAction.class);
            if (twoPhaseBusinessAction != null) {
                isTccClazz = true;
                break;
            }
        }
        if (!isTccClazz) {
            return false;
        }
        short protocols = remotingDesc.getProtocol();
        //LocalTCC
        if (Protocols.IN_JVM == protocols) {
            //in jvm TCC bean , AOP
            return true;
        }
        // sofa:reference /  dubbo:reference, AOP
        return remotingDesc.isReference();
    }

    /**
     * get remoting bean info: sofa:service, sofa:reference, dubbo:reference, dubbo:service
     *
     * @param bean     the bean
     * @param beanName the bean name
     * @return if sofa:service, sofa:reference, dubbo:reference, dubbo:service return true, else return false
     */
    protected static boolean parserRemotingServiceInfo(Object bean, String beanName) {
        RemotingParser remotingParser = DefaultRemotingParser.get().isRemoting(bean, beanName);
        if (remotingParser != null) {
            return DefaultRemotingParser.get().parserRemotingServiceInfo(bean, beanName, remotingParser) != null;
        }
        return false;
    }

    /**
     * get the remoting description of TCC bean
     *
     * @param beanName the bean name
     * @return remoting desc
     */
    public static RemotingDesc getRemotingDesc(String beanName) {
        return DefaultRemotingParser.get().getRemotingBeanDesc(beanName);
    }

    /**
     * Create a proxy bean for tcc service
     *
     * @param interfaceClass
     * @param fieldValue
     * @param actionInterceptor
     * @return
     */
    public static <T> T createProxy(Class<T> interfaceClass, Object fieldValue, TccActionInterceptor actionInterceptor) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(fieldValue);
        factory.setInterfaces(interfaceClass);
        factory.addAdvice(actionInterceptor);

        return (T) factory.getProxy();
    }
}
