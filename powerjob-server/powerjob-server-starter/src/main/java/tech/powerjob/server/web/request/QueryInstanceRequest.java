package tech.powerjob.server.web.request;

import lombok.Data;
import tech.powerjob.server.common.constants.InstanceType;

import java.util.List;

/**
 * 任务实例查询对象
 *
 * @author tjq
 * @since 2020/4/14
 */
@Data
public class QueryInstanceRequest {

    /**
     * 任务所属应用ID
     */
    private Long appId;
    /**
     * 当前页码
     */
    private Integer index;
    /**
     * 页大小
     */
    private Integer pageSize;
    /**
     * 查询条件（NORMAL/WORKFLOW）
     */
    private InstanceType type;
    private Long instanceId;
    private Long jobId;

    /**
     * 任务名称
     */
    private String jobName;

    private Long wfInstanceId;

    private String status;

    private List<Long> triggerTime;
}
