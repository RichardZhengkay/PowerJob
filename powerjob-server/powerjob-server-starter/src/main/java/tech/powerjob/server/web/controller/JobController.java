package tech.powerjob.server.web.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import tech.powerjob.common.request.http.SaveJobInfoRequest;
import tech.powerjob.common.response.ResultDTO;
import tech.powerjob.server.common.constants.SwitchableStatus;
import tech.powerjob.server.persistence.PageResult;
import tech.powerjob.server.persistence.remote.model.JobInfoDO;
import tech.powerjob.server.persistence.remote.repository.JobInfoRepository;
import tech.powerjob.server.core.service.JobService;
import tech.powerjob.server.web.request.QueryJobInfoRequest;
import tech.powerjob.server.web.response.JobInfoVO;
import lombok.extern.slf4j.Slf4j;
import javax.persistence.criteria.Predicate;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务信息管理 Controller
 *
 * @author tjq
 * @since 2020/3/30
 */
@Slf4j
@RestController
@RequestMapping("/job")
public class JobController {

    @Resource
    private JobService jobService;
    @Resource
    private JobInfoRepository jobInfoRepository;

    @PostMapping("/save")
    public ResultDTO<Void> saveJobInfo(@RequestBody SaveJobInfoRequest request) {
        jobService.saveJob(request);
        return ResultDTO.success(null);
    }

    @PostMapping("/copy")
    public ResultDTO<JobInfoVO> copyJob(String jobId) {
        return ResultDTO.success(JobInfoVO.from(jobService.copyJob(Long.valueOf(jobId))));
    }

    @GetMapping("/export")
    public ResultDTO<SaveJobInfoRequest> exportJob(String jobId) {
        return ResultDTO.success(jobService.exportJob(Long.valueOf(jobId)));
    }

    @GetMapping("/disable")
    public ResultDTO<Void> disableJob(String jobId) {
        jobService.disableJob(Long.valueOf(jobId));
        return ResultDTO.success(null);
    }

    @GetMapping("/delete")
    public ResultDTO<Void> deleteJob(String jobId) {
        jobService.deleteJob(Long.valueOf(jobId));
        return ResultDTO.success(null);
    }

    @GetMapping("/run")
    public ResultDTO<Long> runImmediately(String appId, String jobId, @RequestParam(required = false) String instanceParams) {
        return ResultDTO.success(jobService.runJob(Long.valueOf(appId), Long.valueOf(jobId), instanceParams, 0L));
    }

    @PostMapping("/list")
    public ResultDTO<PageResult<JobInfoVO>> listJobs(@RequestBody QueryJobInfoRequest request) {

        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        PageRequest pageRequest = PageRequest.of(request.getIndex(), request.getPageSize(), sort);
        Page<JobInfoDO> jobInfoPage;

        Specification<JobInfoDO> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("appId"), request.getAppId()));
            predicates.add(cb.notEqual(root.get("status"), SwitchableStatus.DELETED.getV()));
            if (null != request.getJobId()) {
                predicates.add(cb.equal(root.get("id"), request.getJobId()));
            }
            if (StringUtils.isNotBlank(request.getKeyword())) {
                predicates.add(cb.like(root.get("jobName"), "%" + request.getKeyword() + "%"));
            }
            if (null != request.getStatus()) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (null != request.getTimeExpressionType()) {
                predicates.add(cb.equal(root.get("timeExpressionType"), request.getTimeExpressionType()));
            }

            // 使用 CriteriaBuilder 的 and 方法组合所有的 Predicate
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        jobInfoPage = jobInfoRepository.findAll(specification, pageRequest);
        return ResultDTO.success(convertPage(jobInfoPage));
    }


    private static PageResult<JobInfoVO> convertPage(Page<JobInfoDO> jobInfoPage) {
        List<JobInfoVO> jobInfoVOList = jobInfoPage.getContent().stream().map(JobInfoVO::from).collect(Collectors.toList());

        PageResult<JobInfoVO> pageResult = new PageResult<>(jobInfoPage);
        pageResult.setData(jobInfoVOList);
        return pageResult;
    }

}
