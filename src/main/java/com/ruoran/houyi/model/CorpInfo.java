package com.ruoran.houyi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import jakarta.persistence.*;

/**
 * @author renlu
 * created by renlu at 2021/7/14 5:26 下午
 */
@Data
@Entity
@Table(name="corplist")
public class CorpInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    String corpid;
    // 调试接口会直接序列化该实体，密钥与私钥绝不允许出现在响应或日志中。
    @JsonIgnore
    @ToString.Exclude
    String secret;
    String corpname;
    @JsonIgnore
    @ToString.Exclude
    String prikey;
    long limits;
    long timeout;
    long status;
    @Column(name = "`update`")
    long update;
    long lastSeq;
}
