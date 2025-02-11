package tech.powerjob.server.web.controller;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import tech.powerjob.common.OmsConstant;
import tech.powerjob.common.enums.InstanceStatus;
import tech.powerjob.common.response.ResultDTO;
import tech.powerjob.server.common.constants.SwitchableStatus;
import tech.powerjob.server.common.utils.OmsFileUtils;
import tech.powerjob.server.persistence.PageResult;
import tech.powerjob.server.persistence.StringPage;
import tech.powerjob.server.persistence.remote.model.InstanceInfoDO;
import tech.powerjob.server.persistence.remote.repository.InstanceInfoRepository;
import tech.powerjob.server.core.instance.InstanceLogService;
import tech.powerjob.server.core.instance.InstanceService;
import tech.powerjob.server.web.request.QueryInstanceDetailRequest;
import tech.powerjob.server.web.request.QueryInstanceRequest;
import tech.powerjob.server.web.response.InstanceDetailVO;
import tech.powerjob.server.web.response.InstanceInfoVO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * 任务实例 Controller
 *
 * @author tjq
 * @since 2020/4/9
 */
@Slf4j
@RestController
@RequestMapping("/instance")
public class InstanceController {



    @Resource
    private InstanceService instanceService;
    @Resource
    private InstanceLogService instanceLogService;

    @Resource
    private InstanceInfoRepository instanceInfoRepository;

    @GetMapping("/stop")
    public ResultDTO<Void> stopInstance(Long appId,Long instanceId) {
        instanceService.stopInstance(appId,instanceId);
        return ResultDTO.success(null);
    }

    @GetMapping("/retry")
    public ResultDTO<Void> retryInstance(String appId, Long instanceId) {
        instanceService.retryInstance(Long.valueOf(appId), instanceId);
        return ResultDTO.success(null);
    }

    @GetMapping("/detail")
    public ResultDTO<InstanceDetailVO> getInstanceDetail(Long appId, Long instanceId) {
        QueryInstanceDetailRequest queryInstanceDetailRequest = new QueryInstanceDetailRequest();
        queryInstanceDetailRequest.setAppId(appId);
        queryInstanceDetailRequest.setInstanceId(instanceId);
        return getInstanceDetailPlus(queryInstanceDetailRequest);
    }

    @PostMapping("/detailPlus")
    public ResultDTO<InstanceDetailVO> getInstanceDetailPlus(@RequestBody QueryInstanceDetailRequest req) {

        // 非法请求参数校验
        String customQuery = req.getCustomQuery();
        String nonNullCustomQuery = Optional.ofNullable(customQuery).orElse(OmsConstant.NONE);
        if (StringUtils.containsAnyIgnoreCase(nonNullCustomQuery, "delete", "update", "insert", "drop", "CREATE", "ALTER", "TRUNCATE", "RENAME", "LOCK", "GRANT", "REVOKE", "PREPARE", "EXECUTE", "COMMIT", "BEGIN")) {
            throw new IllegalArgumentException("Don't get any ideas about the database, illegally query: " + customQuery);
        }

        // 兼容老版本前端不存在 appId 的场景
        if (req.getAppId() == null) {
            req.setAppId(instanceService.getInstanceInfo(req.getInstanceId()).getAppId());
        }

        return ResultDTO.success(InstanceDetailVO.from(instanceService.getInstanceDetail(req.getAppId(), req.getInstanceId(), customQuery)));
    }

    @GetMapping("/log")
    public ResultDTO<StringPage> getInstanceLog(Long appId, Long instanceId, Long index) {
        return ResultDTO.success(instanceLogService.fetchInstanceLog(appId, instanceId, index));
    }

    @GetMapping("/downloadLogUrl")
    public ResultDTO<String> getDownloadUrl(Long appId, Long instanceId) {
        return ResultDTO.success(instanceLogService.fetchDownloadUrl(appId, instanceId));
    }

    @GetMapping("/downloadLog")
    public void downloadLogFile(Long instanceId , HttpServletResponse response) throws Exception {

        File file = instanceLogService.downloadInstanceLog(instanceId);
        OmsFileUtils.file2HttpResponse(file, response);
    }

    @GetMapping("/downloadLog4Console")
    @SneakyThrows
    public void downloadLog4Console(Long appId, Long instanceId , HttpServletResponse response) {
        // 获取内部下载链接
        String downloadUrl = instanceLogService.fetchDownloadUrl(appId, instanceId);
        // 先下载到本机
        String logFilePath = OmsFileUtils.genTemporaryWorkPath() + String.format("powerjob-%s-%s.log", appId, instanceId);
        File logFile = new File(logFilePath);

        try {
            FileUtils.copyURLToFile(new URL(downloadUrl), logFile);

            // 再推送到浏览器
            OmsFileUtils.file2HttpResponse(logFile, response);
        } finally {
            FileUtils.forceDelete(logFile);
        }
    }

    @PostMapping("/list")
    public ResultDTO<PageResult<InstanceInfoVO>> list(@RequestBody QueryInstanceRequest request) {

        Sort sort = Sort.by(Sort.Direction.DESC, "actualTriggerTime");
        PageRequest pageable = PageRequest.of(request.getIndex(), request.getPageSize(), sort);

        Specification<InstanceInfoDO> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("type"), request.getType().getV()));
            predicates.add(cb.notEqual(root.get("status"), SwitchableStatus.DELETED.getV()));
            predicates.add(cb.equal(root.get("appId"), request.getAppId()));

            if (null != request.getJobId()) {
                predicates.add(cb.equal(root.get("jobId"), request.getJobId()));
            }
            if (StringUtils.isNotBlank(request.getJobName())) {
                predicates.add(cb.like(root.get("jobName"), "%" + request.getJobName() + "%"));
            }
            if (StringUtils.isNoneBlank(request.getStatus())) {
                predicates.add(cb.equal(root.get("status"), InstanceStatus.valueOf(request.getStatus()).getV()));
            }
            if (!CollectionUtils.isEmpty(request.getTriggerTime())) {
                predicates.add(cb.between(root.get("actualTriggerTime"), request.getTriggerTime().get(0), request.getTriggerTime().get(1)));
            }

            // 使用 CriteriaBuilder 的 and 方法组合所有的 Predicate
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<InstanceInfoDO> pageResult = instanceInfoRepository.findAll(specification, pageable);
        return ResultDTO.success(convertPage(pageResult));
    }

    private PageResult<InstanceInfoVO> convertPage(Page<InstanceInfoDO> page) {
        PageResult<InstanceInfoVO> pageResult = new PageResult<>(page);
        pageResult.setData(page.getContent().stream().map(InstanceInfoVO::from).collect(Collectors.toList()));
        return pageResult;
    }

}
