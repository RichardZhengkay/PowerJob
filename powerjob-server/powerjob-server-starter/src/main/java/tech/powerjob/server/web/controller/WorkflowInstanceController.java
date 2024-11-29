package tech.powerjob.server.web.controller;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import tech.powerjob.common.enums.WorkflowInstanceStatus;
import tech.powerjob.common.response.ResultDTO;
import tech.powerjob.server.persistence.PageResult;
import tech.powerjob.server.persistence.remote.model.WorkflowInstanceInfoDO;
import tech.powerjob.server.persistence.remote.repository.WorkflowInstanceInfoRepository;
import tech.powerjob.server.core.workflow.WorkflowInstanceService;
import tech.powerjob.server.web.request.QueryWorkflowInstanceRequest;
import tech.powerjob.server.web.response.WorkflowInstanceInfoVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流实例控制器
 *
 * @author tjq
 * @since 2020/5/31
 */
@RestController
@RequestMapping("/wfInstance")
public class WorkflowInstanceController {

    @Resource
    private WorkflowInstanceService workflowInstanceService;
    @Resource
    private WorkflowInstanceInfoRepository workflowInstanceInfoRepository;

    @GetMapping("/stop")
    public ResultDTO<Void> stopWfInstance(Long wfInstanceId, Long appId) {
        workflowInstanceService.stopWorkflowInstanceEntrance(wfInstanceId, appId);
        return ResultDTO.success(null);
    }

    @RequestMapping("/retry")
    public ResultDTO<Void> retryWfInstance(Long wfInstanceId, Long appId) {
        workflowInstanceService.retryWorkflowInstance(wfInstanceId, appId);
        return ResultDTO.success(null);
    }

    @RequestMapping("/markNodeAsSuccess")
    public ResultDTO<Void> markNodeAsSuccess(Long wfInstanceId, Long appId, Long nodeId) {
        workflowInstanceService.markNodeAsSuccess(appId, wfInstanceId, nodeId);
        return ResultDTO.success(null);
    }


    @GetMapping("/info")
    public ResultDTO<WorkflowInstanceInfoVO> getInfo(Long wfInstanceId, Long appId) {
        WorkflowInstanceInfoDO wfInstanceDO = workflowInstanceService.fetchWfInstance(wfInstanceId, appId);
        return ResultDTO.success(WorkflowInstanceInfoVO.from(wfInstanceDO));
    }

    @PostMapping("/list")
    public ResultDTO<PageResult<WorkflowInstanceInfoVO>> listWfInstance(@RequestBody QueryWorkflowInstanceRequest req) {
        Sort sort = Sort.by(Sort.Direction.DESC, "actualTriggerTime");
        PageRequest pageable = PageRequest.of(req.getIndex(), req.getPageSize(), sort);

        Specification<WorkflowInstanceInfoDO> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("appId"), req.getAppId()));

            if (null != req.getWorkflowId()) {
                Predicate workflowIdPredicate = cb.equal(root.get("workflowId"), req.getWorkflowId());
                predicates.add(workflowIdPredicate);
            }

            if (StringUtils.isNotBlank(req.getWorkflowName())) {
                Predicate workflowNamePredicate = cb.like(root.get("workflowName"), "%" + req.getWorkflowName() + "%");
                predicates.add(workflowNamePredicate);
            }

            if (StringUtils.isNoneBlank(req.getStatus())) {
                Predicate statusPredicate = cb.equal(root.get("status"), WorkflowInstanceStatus.valueOf(req.getStatus()).getV());
                predicates.add(statusPredicate);
            }

            if (!CollectionUtils.isEmpty(req.getTriggerTime())) {
                Predicate actualTriggerTimePredicate = cb.between(root.get("actualTriggerTime"), req.getTriggerTime().get(0), req.getTriggerTime().get(1));
                predicates.add(actualTriggerTimePredicate);
            }

            // 使用 CriteriaBuilder 的 and 方法组合所有的 Predicate
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<WorkflowInstanceInfoDO> ps = workflowInstanceInfoRepository.findAll(specification, pageable);

        return ResultDTO.success(convertPage(ps));
    }

    private PageResult<WorkflowInstanceInfoVO> convertPage(Page<WorkflowInstanceInfoDO> ps) {
        PageResult<WorkflowInstanceInfoVO> pr = new PageResult<>(ps);
        pr.setData(ps.getContent().stream().map(WorkflowInstanceInfoVO::from).collect(Collectors.toList()));
        return pr;
    }
}
