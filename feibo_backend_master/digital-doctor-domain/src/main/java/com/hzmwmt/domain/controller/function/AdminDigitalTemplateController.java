package com.hzmwmt.domain.controller.function;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.entity.function.TDigitalTemplate;
import com.hzmwmt.domain.service.function.AdminDigitalTemplateService;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/digital-template")
public class AdminDigitalTemplateController {
    @Resource private AdminDigitalTemplateService adminDigitalTemplateService;
    @Resource private ObjectMapper objectMapper;
    @GetMapping("/page") public Page<TDigitalTemplate> page(@RequestParam(defaultValue="1") Integer pageNum,
            @RequestParam(defaultValue="10") Integer pageSize,@RequestParam(required=false) String title,@RequestParam(required=false) Long userId) {
        return adminDigitalTemplateService.page(new Page<>(Math.max(1,pageNum),Math.min(100,Math.max(1,pageSize))),
            new LambdaQueryWrapper<TDigitalTemplate>().like(title!=null&&!title.isBlank(),TDigitalTemplate::getTitle,title)
                .eq(userId!=null,TDigitalTemplate::getUserId,userId).orderByDesc(TDigitalTemplate::getId));
    }
    @PostMapping @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="新增",description="新增数字模板",recordParams=false,recordResult=false,critical=true)
    public TDigitalTemplate add(@RequestBody TDigitalTemplate request) {
        TDigitalTemplate template=fields(request);template.setDigitalTemplateId(IdWorker.getId());
        // The existing system-template catalogue selects user_id IS NULL.
        template.setUserId(null);
        if(!adminDigitalTemplateService.save(template)) throw new ResponseStatusException(HttpStatus.CONFLICT,"模板未创建");
        return template;
    }
    @GetMapping("/{id}") public TDigitalTemplate getById(@PathVariable Integer id) { return adminDigitalTemplateService.getById(id); }
    @PutMapping @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="修改",description="修改数字模板",recordParams=false,recordResult=false,critical=true)
    public boolean update(@RequestBody TDigitalTemplate request) {
        if(request.getId()==null || request.getId()<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板ID无效");
        TDigitalTemplate template=fields(request);template.setId(request.getId());
        return adminDigitalTemplateService.updateById(template);
    }
    @DeleteMapping("/{id}") @Transactional(rollbackFor=Exception.class)
    @OperationLog(operationType="删除",description="删除数字模板",recordParams=false,recordResult=false,targetIdParameterIndex=0,critical=true)
    public boolean delete(@PathVariable Integer id) { return adminDigitalTemplateService.removeById(id); }
    private TDigitalTemplate fields(TDigitalTemplate request) {
        if(request.getTitle()==null || request.getTitle().isBlank() || request.getTitle().length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板标题须为1到200字");
        if(request.getBody()==null || request.getBody().length()>1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入有效的模板JSON");
        try { if(!objectMapper.readTree(request.getBody()).isObject()) throw new IllegalArgumentException(); }
        catch(Exception exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模板内容必须是JSON对象"); }
        TDigitalTemplate template=new TDigitalTemplate();template.setTitle(request.getTitle().trim());template.setBody(request.getBody());return template;
    }
}
