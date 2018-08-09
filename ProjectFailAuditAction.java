package com.jzjy.erp.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.seasar.struts.annotation.ActionForm;
import org.seasar.struts.annotation.Execute;
import org.seasar.struts.util.ResponseUtil;

import com.google.gson.reflect.TypeToken;
import com.jzjy.erp.Code;
import com.jzjy.erp.entity.ErpBuHuo;
import com.jzjy.erp.entity.ErpBuHuo.BuhuoStatus;
import com.jzjy.erp.entity.ErpCexu;
import com.jzjy.erp.entity.ErpCexu.CexuStatus;
import com.jzjy.erp.entity.ErpCouTi;
import com.jzjy.erp.entity.ErpCouTi.CouTiStatus;
import com.jzjy.erp.entity.ErpDelivery;
import com.jzjy.erp.entity.ErpDelivery.DeliveryStatus;
import com.jzjy.erp.entity.ErpJianKu;
import com.jzjy.erp.entity.ErpJianKu.JiankuStatus;
import com.jzjy.erp.entity.ErpProcess;
import com.jzjy.erp.entity.ErpProduct;
import com.jzjy.erp.entity.ErpProduct.BusinessRequirementType;
import com.jzjy.erp.entity.ErpProject;
import com.jzjy.erp.entity.ErpProject.Failed;
import com.jzjy.erp.entity.ErpProjectFailAudit;
import com.jzjy.erp.entity.ErpProjectFailAudit.FailAuditProcessLink;
import com.jzjy.erp.entity.ErpProjectFailAudit.FailAuditStatus;
import com.jzjy.erp.entity.ErpReport;
import com.jzjy.erp.entity.ErpReport.ReportStatus;
import com.jzjy.erp.entity.ErpSample;
import com.jzjy.erp.entity.ErpUserbase;
import com.jzjy.erp.entity.ErpYidaics;
import com.jzjy.erp.entity.ErpYidaics.YidaicsStatus;
import com.jzjy.erp.form.ProjectFailAuditForm;
import com.jzjy.erp.object.ResultObject;
import com.jzjy.erp.service.CaptureService;
import com.jzjy.erp.service.ExecutionService;
import com.jzjy.erp.service.ExtractionService;
import com.jzjy.erp.service.MatureExperimentService;
import com.jzjy.erp.service.MatureIssuedService;
import com.jzjy.erp.service.ProcessService;
import com.jzjy.erp.service.ProductService;
import com.jzjy.erp.service.ProjectFailAuditService;
import com.jzjy.erp.service.ProjectSendService;
import com.jzjy.erp.service.ProjectService;
import com.jzjy.erp.service.ReportAuditService;
import com.jzjy.erp.service.SampleService;
import com.jzjy.erp.service.SequencingManageService;
import com.jzjy.erp.service.TiterService;
import com.jzjy.erp.utils.DateUtil;
import com.jzjy.erp.utils.JsonUtil;
import com.jzjy.erp.utils.SessionUtil;
import com.jzjy.erp.utils.StringHelper;

/**
 * Description: 失败项目审核
 * @version 1.0  2018-4-27 下午6:01:29 王斌 (wangb@unimlink.com) created
 */
@SuppressWarnings("rawtypes")
public class ProjectFailAuditAction extends ActionBase{
	
	Logger logger = Logger.getLogger(ProjectFailAuditAction.class);
	
	@ActionForm
	@Resource
	ProjectFailAuditForm projectFailAuditForm;
	
	@Resource
	ProjectFailAuditService projectFailAuditService;
	@Resource
	ExecutionService executionService;
	@Resource
	ProcessService processService;
	@Resource
	ProductService productService;
	@Resource
	SampleService sampleService;
	@Resource
	ProjectService projectService;
	@Resource
	ExtractionService extractionService;
	@Resource
	TiterService titerService;
	@Resource
	CaptureService captureService;
	@Resource
	SequencingManageService sequencingManageService;
	@Resource
	MatureExperimentService matureExperimentService;
	@Resource
	MatureIssuedService matureIssuedService;
	@Resource
	ReportAuditService reportAuditService;
	@Resource
	ProjectSendService projectSendService;
	
	@Execute(validator = false)
	public String index() {
		return "index.jsp";
	}
	/**
	 * Description: 失败项目审核列表
	 * @Version 1.0 2018-4-27 下午6:05:01 王斌(wangb@unimlink.com) 创建
	 * @return
	 */
	@Execute(validator = false)
	public String projectFailAuditList() {
		
		try {
			ResultObject resultObject = new ResultObject();
			List<Map> list = this.projectFailAuditService.findFailAuditList(projectFailAuditForm);
			if (list != null && !list.isEmpty()) {
				resultObject.setCode(Code.CODE_1);
				resultObject.setRows(list);
				resultObject.setTotal(this.projectFailAuditService.findFailAuditCount(projectFailAuditForm));
			} else {
				resultObject.setRows(new ArrayList());
			}
			ResponseUtil.write(JsonUtil.ObjectToJson(resultObject), "utf-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Description: 审核通过
	 * @Version 1.0 2018-4-27 下午6:08:57 王斌(wangb@unimlink.com) 创建
	 * @return
	 */
	@Execute(validator = false)
	public String auditSuccess() {
		
		try {
			ErpUserbase userbase = SessionUtil.getUserSession(session);
			
			Integer id = projectFailAuditForm.getId();
			if(id == null){
				logger.error("auditSuccess : id is null.");
				return outError("审核失败。");
			}
			ErpProjectFailAudit audit = this.projectFailAuditService.findById(id);
			if(audit == null){
				logger.error("auditSuccess : audit is null.");
				return outError("审核失败。");
			}
			
			//FailAuditProcessLink
			//流程环节 1:抽提 2:浓度测定 3:捕获 4:测序 5:一代成熟实验 6:一代位点测序预判 7:报告制作
			boolean flag = this.auditSuccess(audit);
			if(flag){
				audit.setOperdetails(this.projectFailAuditService.setOperdetails(audit.getOperdetails(), userbase, "审核通过。"));
				audit.setStatus(FailAuditStatus.Success.getValue());
				this.projectFailAuditService.updateProjectFailAudit(audit);
			}
			return outMsg(Code.CODE_1, "审核成功。");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Description: 审核失败
	 * @Version 1.0 2018-4-27 下午6:08:49 王斌(wangb@unimlink.com) 创建
	 */
	@Execute(validator = false)
	public String auditFailed() {
		
		try {
			ErpUserbase userbase = SessionUtil.getUserSession(session);
			
			Integer id = projectFailAuditForm.getId();
			String reason = projectFailAuditForm.getReason();
			if(id == null){
				logger.error("auditFailed : id is null.");
				return outError("审核失败。");
			}
			if(StringUtils.isBlank(reason)){
				logger.error("auditFailed : reason is null.");
				return outError("审核失败。");
			}
			ErpProjectFailAudit audit = this.projectFailAuditService.findById(id);
			if(audit == null){
				logger.error("auditFailed : audit is null.");
				return outError("审核失败。");
			}
			
			audit.setOperdetails(this.projectFailAuditService.setOperdetails(audit.getOperdetails(), userbase, "审核失败! "+reason));
			audit.setStatus(FailAuditStatus.Failure.getValue());
			this.projectFailAuditService.updateProjectFailAudit(audit);
			return outMsg(Code.CODE_1, "操作成功。");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Description: 审核通过
	 * @Version 1.0 2018-4-28 下午2:41:07 王斌(wangb@unimlink.com) 创建
	 */
	private boolean auditSuccess(ErpProjectFailAudit audit) throws Exception {
		
		if (FailAuditProcessLink.Extraction.getValue() == audit.getProcessLink()) {// 抽提
			return this.extraction(audit);
		} else if (FailAuditProcessLink.Titer.getValue() == audit.getProcessLink()) {// 浓度测定
			return this.titer(audit);
		} else if (FailAuditProcessLink.Capture.getValue() == audit.getProcessLink()) {// 捕获
			return this.capture(audit);
		} else if (FailAuditProcessLink.Sequencing.getValue() == audit.getProcessLink()) {// 测序
			return this.sequencing(audit);
		} else if (FailAuditProcessLink.MatureExperiment.getValue() == audit.getProcessLink()) {// 一代成熟实验
			return this.matureExperiment(audit);
		} else if (FailAuditProcessLink.SequencingJudge.getValue() == audit.getProcessLink()) {// 测序预判
			// return this.sequencingJudge(audit); //TODO 与东亮沟通，测序预判不做失败审核处理 by wangbin 20180507
		} else if (FailAuditProcessLink.ReportMake.getValue() == audit.getProcessLink()) {// 报告制作
			return this.reportMake(audit);
		} else if (FailAuditProcessLink.ProjectSend.getValue() == audit.getProcessLink()) {//外送项目检测
			return this.projectSend(audit);
		}
		return false;
	}
	
	/**
	 * Description: 抽提
	 * @Version 1.0 2018-4-28 下午2:51:20 王斌(wangb@unimlink.com) 创建
	 */
	private boolean extraction(ErpProjectFailAudit audit) throws Exception{
	//抽提
		ErpCouTi entity = extractionService.findById(Integer.parseInt(audit.getInstanceId()));
		if(entity == null){
			logger.error("extraction : couti is null.");
			return false;
		}
		if(CouTiStatus.Failure.getValue() == entity.getStatus()){
			logger.error("extraction : it's a failed state.");
			return false;
		}
		
		List<ErpSample> slist = this.sampleService.findByChoutiId(entity.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("extraction : sample list is null.");
			return false;
		}
		List<Map<String,Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String,Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if(reasonList != null && !reasonList.isEmpty()){
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		
		entity.setStatus(CouTiStatus.Failure.getValue());
		entity.setUpdated(DateUtil.getSysDateTime());
		int num = extractionService.updateExtraction(entity);
		if (num > 0) {
			//设置样本失败 by wangbin 20180302
			for (ErpSample sample : slist) {
				this.sampleService.failSample(sample);
			}
			
			if(entity.getProjectid().contains(StringHelper.SPLIT_PARAM_THREE)){
				String[] arr = entity.getProjectid().split(StringHelper.SPLIT_PARAM_THREE);
				for (String id : arr) {
					ErpProcess process = executionService.findCurrentProcessStatus(id);
					if(process == null) continue;
					this.projectService.failProject(id, reason);
					this.executionService.failProcess(id, process.getId(), reason, userid);
				}
				return true;
			}else{
				ErpProcess process = executionService.findCurrentProcessStatus(entity.getProjectid());
				this.projectService.failProject(entity.getProjectid(), reason);
				return this.executionService.failProcess(entity.getProjectid(), process.getId(), reason, userid);
			}
		}
		return false;
	}
	
	
	/**
	 * Description: 浓度测定失败
	 * @Version 1.0 2018-5-2 上午10:07:17 王斌(wangb@unimlink.com) 创建
	 */
	private boolean titer(ErpProjectFailAudit audit) throws Exception {
		ErpJianKu entity = this.titerService.findErpProjectbyId(audit.getInstanceId());
		if (entity == null) {
			logger.error("titer : jianku is null.");
			return false;
		}
		if (JiankuStatus.Fail.getValue() == entity.getStatus()) {
			logger.error("titer : jianku has failed.");
			return false;
		}
		ErpProcess process = this.executionService.findCurrentProcessStatus(entity.getProjectid());
		if (process == null) {
			logger.error("titer : process is null.");
			return false;
		}
		List<ErpSample> slist = this.sampleService.findByJiankuId(entity.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("titer : sample list is null.");
			return false;
		}
		
		List<Map<String,Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String,Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if(reasonList != null && !reasonList.isEmpty()){
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		//设置样本失败 by wangbin 20180302
		for (ErpSample sample : slist) {
			this.sampleService.failSample(sample);
		}
		
		entity.setStatus(JiankuStatus.Fail.getValue());
		entity.setUpdated(DateUtil.getSysDateTime());
		int num = this.titerService.updateTiter(entity);
		if (num > 0) {
			//设置项目失败
			this.projectService.failProject(entity.getProjectid(), reason);
			return this.executionService.failProcess(entity.getProjectid(), process.getId(), reason, userid);
		}
		return false;
	}
	
	/**
	 * Description: 捕获失败
	 * @Version 1.0 2018-5-2 上午10:26:39 王斌(wangb@unimlink.com) 创建
	 */
	private boolean capture(ErpProjectFailAudit audit) throws Exception {
		ErpBuHuo entity = this.captureService.findById(Integer.parseInt(audit.getInstanceId()));
		if (entity == null) {
			logger.error("capture : buhuo is null.");
			return false;
		}
		if (BuhuoStatus.Failure.getValue() == entity.getStatus()) {
			logger.error("capture : has failed.");
			return false;
		}
		List<ErpProject> list = this.projectService.findByBuhuoId(entity.getId());
		if (list == null || list.isEmpty()) {
			logger.error("capture : project list is null.");
			return false;
		}
		List<ErpSample> slist = this.sampleService.findByBuhuoId(entity.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("capture : sample list is null.");
			return false;
		}
		
		ErpProcess process = this.executionService.findCurrentProcessStatus(list.get(0).getProjectId());
		if (process == null) {
			logger.error("capture : process is null.");
			return false;
		}
		int num = 0;
		// 设置样本失败 by wangbin 20180302
		for (ErpSample sample : slist) {
			this.sampleService.failSample(sample);
		}
		
		List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if (reasonList != null && !reasonList.isEmpty()) {
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		for (ErpProject project : list) {
			boolean flag = this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
			if (flag) {
				this.projectService.failProject(project.getProjectId(), reason);
				num++;
			}
		}
		
		if (num > 0) {
			entity.setStatus(BuhuoStatus.Failure.getValue());
			num = this.captureService.updateCapture(entity);
			return (num > 0);
		}
		return false;
	}
	
	/**
	 * Description: 测序失败
	 * @Version 1.0 2018-5-2 上午10:41:37 王斌(wangb@unimlink.com) 创建
	 */
	private boolean sequencing(ErpProjectFailAudit audit) throws Exception{
			ErpProject project = projectService.findByProjectId(audit.getInstanceId());
			if (project == null) {
				logger.error("sequencing : project is null.");
				return false;
			}
			if(Failed.Yes.getValue() == project.getFailed()){
				logger.error("sequencing : project has failed.");
				return  false;
			}
			ErpProcess process = this.executionService.findCurrentProcessStatus(project.getProjectId());
			if (process == null) {
				logger.error("sequencing : process is null.");
				return  false;
			}
			ErpSample sample = this.sampleService.findByProjectId(project.getId());
			if (sample == null) {
				logger.error("sequencing : sample is null.");
				return  false;
			}
			
			List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
			String reason = "-";
			Integer userid = null;
			if (reasonList != null && !reasonList.isEmpty()) {
				reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
				userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
			}
			
			int number = this.sampleService.failSample(sample);
			int num = this.projectService.failProject(project.getProjectId(), reason);
			if (num > 0 && number > 0) {
				// 设置样本失败 by wangbin 20180302
				// 执行失败流程
				boolean flag = this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
 				if (flag) {
 					//检测测序单下所有关联项目是否全部成功
 					List<String> list = this.projectService.findErpCexuByprojectId(project.getCexuId());
 					if (list == null || list.isEmpty()) {
 						ErpCexu ec = this.sequencingManageService.findById(project.getCexuId());
 						ec.setStatus(CexuStatus.Success.getValue());
 						ec.setUpdated(DateUtil.getSysDateTime());
 						int count = this.sequencingManageService.updateSequencing(ec);
 						if (count > 0) {
 							logger.error("sequencing ：测序单[" + ec.getLaneId() + "]测序成功。");
 						} else {
 							logger.error("sequencing ：测序单[" + ec.getLaneId() + "]测序失败。");
 						}
 					}
					return true;
				}
			}
			return  false;
	}
	
	/**
	 * Description: 一代成熟实验失败
	 * @Version 1.0 2018-5-2 上午11:22:48 王斌(wangb@unimlink.com) 创建
	 */
	private boolean matureExperiment(ErpProjectFailAudit audit) throws Exception{
		ErpYidaics yidaics = this.matureExperimentService.findById(Integer.parseInt(audit.getInstanceId()));
		if(yidaics == null){
			logger.error("matureExperiment : yidaics is null.");
			return false;
		}
		if(YidaicsStatus.Fail.getValue() == yidaics.getStatus()){
			logger.error("matureExperiment : yidaics failed.");
			return false;
		}
		ErpProject project = this.matureIssuedService.findProjectIdById(yidaics.getProjectId());
		if (project == null || StringUtils.isBlank(project.getProjectId())) {
			logger.error("matureExperiment : project is null.");
			return false;
		}
		ErpProcess process = executionService.findCurrentProcessStatus(project.getProjectId());
		if (process == null) {
			logger.error("matureExperiment : process is null.");
			return false;
		}
		ErpProduct product = this.productService.findProductByProjectId(yidaics.getProjectId());
		if (product == null) {
			logger.error("matureExperiment : product is null.");
			return false;
		}
		ErpSample sample = this.sampleService.findByProjectId(yidaics.getProjectId());
		if(sample == null){
			logger.error("matureExperiment : sample is null.");
			return false;
		}
		
		List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if (reasonList != null && !reasonList.isEmpty()) {
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		yidaics.setStatus(YidaicsStatus.Fail.getValue());
		int result = this.matureExperimentService.updateYidaics(yidaics);
		if(result > 0){
			this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
			//将项目设为失败
			this.projectService.failProject(project.getProjectId(), reason);
			//设置样本失败 by wangbin 20180302
			this.sampleService.failSample(sample);
			
			// TODO 加项类产品 恢复项目 by wangbin 20180424
			if (product.getBrtype() == BusinessRequirementType.AddedItem.getValue() && StringUtils.isNotBlank(sample.getMainProjectId())) {
				// 恢复主项目
				this.executionService.recoveryProcess(sample.getMainProjectId(), userid);
				
				// 恢复相关家系项目
				List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(sample.getMainProjectId());
				for (ErpProject entity : list) {
					this.executionService.recoveryProcess(entity.getProjectId(), userid);
				}
			}
			
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 一代位点测序预判失败
	 * @Version 1.0 2018-5-2 下午2:56:17 王斌(wangb@unimlink.com) 创建
	 */
	@SuppressWarnings("unused")
	private boolean sequencingJudge(ErpProjectFailAudit audit) throws Exception {
		
		ErpReport report = this.reportAuditService.findById(Integer.parseInt(audit.getInstanceId()));
		if (report == null) {
			logger.error("sequencingJudge : report is null.");
			return false;
		}
		
		List<ErpSample> slist = this.sampleService.findByReportId(report.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("sequencingJudge : sample list is null.");
			return false;
		}
		
		ErpProduct product = this.productService.findErpProductbyId(report.getProductId());
		if (product == null) {
			logger.error("sequencingJudge : product is null.");
			return false;
		}
		ErpSample sentity = this.sampleService.findBySampleId1(report.getSampleId());
		if(sentity == null){
			logger.error("sequencingJudge : sample is null.");
			return false;
		}
		
		ErpProcess process = this.executionService.findCurrentProcessStatus(report.getReportId());
		if (process == null) {
			logger.error("sequencingJudge : process is null.");
			return false;
		}
		
		List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if (reasonList != null && !reasonList.isEmpty()) {
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		if (this.processService.checkCurrentProcessForCexuJudgment(report.getReportId())) {
			//设置样本失败 by wangbin 20180302
			for (ErpSample sample : slist) {
				this.sampleService.failSample(sample);
			}
			
			this.executionService.failProcess(report.getReportId(), process.getId(), reason, userid);
			this.projectService.failProject(report.getReportId(), reason);
			List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(report.getReportId());
			// 关联家系执行流程
			if (list != null && !list.isEmpty()) {
				for (ErpProject project : list) {
					if (this.processService.checkCurrentProcessForCexuJudgment(project.getProjectId()))
						this.projectService.failProject(project.getProjectId(),reason);
						this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
				}
			}
			
			// TODO 加项类产品 恢复项目 by wangbin 20180424
			if (product.getBrtype() == BusinessRequirementType.AddedItem.getValue() && StringUtils.isNotBlank(sentity.getMainProjectId())) {
				// 恢复主项目
				this.executionService.recoveryProcess(sentity.getMainProjectId(), userid);
				
				// 恢复相关家系项目
				List<ErpProject> plist = this.projectService.findFamilyProjectByProjectId(sentity.getMainProjectId());
				for (ErpProject entity : plist) {
					this.executionService.recoveryProcess(entity.getProjectId(), userid);
				}
			}
			
			return true;
		}
		return false;
	}
	
	
	/**
	 * Description: 报告制作失败
	 * @Version 1.0 2018-5-2 下午3:22:17 王斌(wangb@unimlink.com) 创建
	 */
	private boolean reportMake(ErpProjectFailAudit audit) throws Exception{
		ErpReport entity = this.reportAuditService.findById(Integer.parseInt(audit.getInstanceId()));
		if (entity == null) {
			logger.error("reportMake : report is null.");
			return false;
		}
		ErpProject project = this.projectService.findByProjectId(entity.getReportId());
		if (project == null) {
			logger.error("reportMake : project is null.");
			return false;
		}
		
		ErpProcess process = this.executionService.findCurrentProcessStatus(project.getProjectId());
		if (process == null) {
			logger.error("reportMake : process is null.");
			return false;
		}
		List<ErpSample> slist = this.sampleService.findByReportId(entity.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("reportMake : sample list is null.");
			return false;
		}
		
		List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if (reasonList != null && !reasonList.isEmpty()) {
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		entity.setStatus(ReportStatus.Fail.getValue());
		entity.setNote(reason);
		entity.setUpdated(DateUtil.getSysDateTime());
		int num = this.reportAuditService.updateReport(entity);
		if(num > 0){
			//设置样本失败 by wangbin 20180302
			for (ErpSample sample : slist) {
				this.sampleService.failSample(sample);
			}
			
			this.projectService.failProject(project.getProjectId(), reason);
			this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
			
			//处理家系
			List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(project.getProjectId());
			for (ErpProject pro : list) {
				this.projectService.failProject(pro.getProjectId(), reason);
				this.executionService.failProcess(pro.getProjectId(), process.getId(), reason, userid);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 外送项目
	 * @Version 1.0 2018-5-9 下午4:19:50 王斌(wangb@unimlink.com) 创建
	 */
	private boolean projectSend(ErpProjectFailAudit audit) throws Exception{
		
		ErpDelivery delivery = this.projectSendService.findDeliveryById(Integer.parseInt(audit.getInstanceId()));
		if (delivery == null) {
			logger.error("projectSend : delivery is null.");
			return false;
		}
		
		List<ErpProject> plist = this.projectSendService.findProjectsByDelId(delivery.getId());
		if (plist == null || plist.isEmpty()) {
			logger.error("projectSend : plist is null.");
			return false;
		}
		
		ErpProcess process = this.executionService.findCurrentProcessStatus(plist.get(0).getProjectId());
		if (process == null) {
			logger.error("projectSend : process is null.");
			return false;
		}
		List<ErpSample> slist = this.sampleService.findByDeliveryId(delivery.getId());
		if (slist == null || slist.isEmpty()) {
			logger.error("projectSend : sample list is null.");
			return false;
		}
		
		List<Map<String, Object>> reasonList = JsonUtil.JsonToBean(audit.getOperdetails(), new TypeToken<List<Map<String, Object>>>() {});
		String reason = "-";
		Integer userid = null;
		if (reasonList != null && !reasonList.isEmpty()) {
			reason = StringHelper.convertStr(reasonList.get(0).get("reason"));
			userid = Integer.parseInt(StringHelper.convertStr(reasonList.get(0).get("operuser")));
		}
		
		delivery.setStatus(DeliveryStatus.Failure.getValue());
		delivery.setNote(DateUtil.getSysDateTime()+" - "+reason);
		int num = this.projectSendService.updateDelivery(delivery);
		if(num > 0){
			//设置样本失败 by wangbin 20180302
			for (ErpSample sample : slist) {
				this.sampleService.failSample(sample);
			}
			
			for (ErpProject project : plist) {
				this.projectService.failProject(project.getProjectId(), reason);
				this.executionService.failProcess(project.getProjectId(), process.getId(), reason, userid);
				
				//处理家系
				List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(project.getProjectId());
				for (ErpProject pro : list) {
					this.projectService.failProject(pro.getProjectId(), reason);
					this.executionService.failProcess(pro.getProjectId(), process.getId(), reason, userid);
				}
			}
			return true;
		}
		return false;
	}
}
