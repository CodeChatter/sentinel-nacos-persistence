/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller;

import java.util.Date;
import java.util.List;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.client.SentinelApiClient;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.repository.rule.InMemDegradeRuleStore;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.util.StringUtil;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller regarding APIs of degrade rules. Refactored since 1.8.0.
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */

@RestController
@RequestMapping(value = "/degrade")
public class DegradeController {

    private final Logger logger = LoggerFactory.getLogger(DegradeController.class);

    @Autowired
    private InMemDegradeRuleStore repository;

    @Autowired
    @Qualifier("degradeRuleNacosProvider")
    private DynamicRuleProvider<List<DegradeRuleEntity>> ruleProvider;
    @Autowired
    @Qualifier("degradeRuleNacosPublisher")
    private DynamicRulePublisher<List<DegradeRuleEntity>> rulePublisher;
    /*@Autowired
    private SentinelApiClient sentinelApiClient;*/

    @GetMapping("/rules.json")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<DegradeRuleEntity>> queryMachineRules(String app, String ip, Integer port) {

        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isEmpty(ip)) {
            return Result.ofFail(-1, "ip can't be null or empty");
        }
        if (port == null) {
            return Result.ofFail(-1, "port can't be null");
        }
        try {
//            List<DegradeRuleEntity> rules = sentinelApiClient.fetchDegradeRuleOfMachine(app, ip, port);
            //去nacos中取数据
            List<DegradeRuleEntity> rules = ruleProvider.getRules(app);
            rules = repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("queryApps error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    @PostMapping("/rule")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<DegradeRuleEntity> add(@RequestBody DegradeRuleEntity entity) {
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getIp())) {
            return Result.ofFail(-1, "ip can't be null or empty");
        }
        if (entity.getPort() == null) {
            return Result.ofFail(-1, "port can't be null");
        }
        if (StringUtil.isBlank(entity.getLimitApp())) {
            return Result.ofFail(-1, "limitApp can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getResource())) {
            return Result.ofFail(-1, "resource can't be null or empty");
        }
        if (entity.getCount() == null) {
            return Result.ofFail(-1, "count can't be null");
        }
        if (entity.getTimeWindow() == null) {
            return Result.ofFail(-1, "timeWindow can't be null");
        }
        if (entity.getGrade() == null) {
            return Result.ofFail(-1, "grade can't be null");
        }
        if (entity.getGrade() < RuleConstant.DEGRADE_GRADE_RT || entity.getGrade() > RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT) {
            return Result.ofFail(-1, "Invalid grade: " + entity.getGrade());
        }
        entity.setApp(entity.getApp().trim());
        entity.setIp(entity.getIp().trim());
        entity.setPort(entity.getPort());
        entity.setLimitApp(entity.getLimitApp().trim());
        entity.setResource(entity.getResource().trim());
        entity.setCount(entity.getCount());
        entity.setTimeWindow(entity.getTimeWindow());
        entity.setGrade(entity.getGrade());
        entity.setMinRequestAmount(entity.getMinRequestAmount());
        entity.setSlowRatioThreshold(entity.getSlowRatioThreshold());
        entity.setStatIntervalMs(entity.getStatIntervalMs());
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
            //推送信息
            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("add error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }
  /*      if (!publishRules(app, ip, port)) {
            logger.info("publish degrade rules fail after rule add");
        }*/
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<DegradeRuleEntity> updateIfNotNull(@PathVariable("id") Long id,
                                                     @RequestBody DegradeRuleEntity entity) {
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }
        if (entity.getGrade() != null) {
            if (entity.getGrade() < RuleConstant.DEGRADE_GRADE_RT || entity.getGrade() > RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT) {
                return Result.ofFail(-1, "Invalid grade: " + entity.getGrade());
            }
        }

        DegradeRuleEntity entity2 = repository.findById(id);

        if (entity2 == null) {
            return Result.ofFail(-1, "id " + id + " dose not exist");
        }

        if (StringUtil.isNotBlank(entity.getApp())) {
            entity2.setApp(entity.getApp().trim());
        }

        if (StringUtil.isNotBlank(entity.getLimitApp())) {
            entity2.setLimitApp(entity.getLimitApp().trim());
        }
        if (StringUtil.isNotBlank(entity.getResource())) {
            entity2.setResource(entity.getResource().trim());
        }
        if (entity.getCount() != null) {
            entity2.setCount(entity.getCount());
        }
        if (entity.getTimeWindow() != null) {
            entity2.setTimeWindow(entity.getTimeWindow());
        }
        if (entity.getGrade() != null) {
            entity2.setGrade(entity.getGrade());
        }
        if (entity.getMinRequestAmount() != null) {
            entity2.setMinRequestAmount(entity.getMinRequestAmount());
        }
        if (entity.getStatIntervalMs() != null) {
            entity2.setStatIntervalMs(entity.getStatIntervalMs());
        }
        if (entity.getSlowRatioThreshold() != null) {
            entity2.setSlowRatioThreshold(entity.getSlowRatioThreshold());
        }
        Date date = new Date();
        entity2.setGmtModified(date);
        try {
            entity2 = repository.save(entity2);
            //推送规则
            publishRules(entity2.getApp());
        } catch (Throwable throwable) {
            logger.error("save error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }
      /*  if (!publishRules(entity.getApp(), entity.getIp(), entity.getPort())) {
            logger.info("publish degrade rules fail after rule update");
        }*/
        return Result.ofSuccess(entity2);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<Long> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }

        DegradeRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }

        try {
            repository.delete(id);
            //推送规则
            publishRules(oldEntity.getApp());
        } catch (Throwable throwable) {
            logger.error("delete error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }
       /* if (!publishRules(oldEntity.getApp(), oldEntity.getIp(), oldEntity.getPort())) {
            logger.info("publish degrade rules fail after rule delete");
        }*/
        return Result.ofSuccess(id);
    }

    /*  private boolean publishRules(String app, String ip, Integer port) {
          List<DegradeRuleEntity> rules = repository.findAllByMachine(MachineInfo.of(app, ip, port));
          return sentinelApiClient.setDegradeRuleOfMachine(app, ip, port, rules);
      }*/
    private void publishRules(String app) throws Exception {
        List<DegradeRuleEntity> rules = repository.findAllByApp(app);
        rulePublisher.publish(app, rules);
    }
}

