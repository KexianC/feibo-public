package com.hzmwmt.domain.service.user.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.user.TFeedback;
import com.hzmwmt.domain.mapper.user.AdminFeedbackMapper;
import com.hzmwmt.domain.service.user.AdminFeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Service
public class AdminFeedbackServiceImpl extends ServiceImpl<AdminFeedbackMapper,TFeedback> implements AdminFeedbackService {
    @Override public IPage<TFeedback> pageQuery(Integer pageNum,Integer pageSize,Long userId,Long type,String keyword,
            Integer status,Integer priority,LocalDate startDate,LocalDate endDate) {
        if(startDate!=null && endDate!=null && startDate.isAfter(endDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"开始日期不能晚于结束日期");
        var query=new LambdaQueryWrapper<TFeedback>()
            .eq(userId!=null,TFeedback::getUserId,userId).eq(type!=null,TFeedback::getType,type)
            .like(StringUtils.hasText(keyword),TFeedback::getTitle,keyword)
            .eq(status!=null,TFeedback::getStatus,status).eq(priority!=null,TFeedback::getPriority,priority);
        if(startDate!=null) query.ge(TFeedback::getCreateTime,startDate.atStartOfDay());
        if(endDate!=null) query.lt(TFeedback::getCreateTime,endDate.plusDays(1).atStartOfDay());
        query.orderByDesc(TFeedback::getCreateTime).orderByDesc(TFeedback::getId);
        return page(new Page<>(pageNum==null?1:Math.max(1,pageNum),pageSize==null?10:Math.min(100,Math.max(1,pageSize))),query);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public TFeedback process(Long id,long version,Integer targetStatus,String reply,Integer priority,Long operatorId) {
        TFeedback existing=getById(id);
        if(existing==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"反馈不存在");
        if(existing.getVersion()==null || existing.getVersion()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"反馈已被其他管理员处理，请刷新后重试");
        if(targetStatus==null || targetStatus<1 || targetStatus>3 || (priority!=null&&(priority<0||priority>3))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"处理参数无效");
        if(existing.getStatus()!=null && existing.getStatus()==3) throw new ResponseStatusException(HttpStatus.CONFLICT,"反馈已关闭");
        if(targetStatus==1 && !StringUtils.hasText(reply)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入处理回复");
        if(reply!=null && reply.length()>5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"回复不能超过5000字");
        TFeedback change=new TFeedback(); change.setStatus(targetStatus);change.setHandlerUserId(operatorId);change.setUpdateTime(LocalDateTime.now());change.setVersion(version+1);
        if(reply!=null) change.setReply(reply.trim());
        if(priority!=null) change.setPriority(priority);
        boolean changed=update(change,new LambdaUpdateWrapper<TFeedback>().eq(TFeedback::getId,id).eq(TFeedback::getVersion,version));
        if(!changed) throw new ResponseStatusException(HttpStatus.CONFLICT,"反馈已被其他管理员处理，请刷新后重试");
        return getById(id);
    }
}
