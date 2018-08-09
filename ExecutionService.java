package com.jzjy.erp.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.gson.reflect.TypeToken;
import com.jzjy.erp.config.JzjyErpConfig;
import com.jzjy.erp.entity.ErpProcess;
import com.jzjy.erp.entity.ErpProcess.DepaCycleType;
import com.jzjy.erp.entity.ErpProcess.Ended;
import com.jzjy.erp.entity.ErpProcess.SubProcesses;
import com.jzjy.erp.entity.ErpProcessCycle;
import com.jzjy.erp.entity.ErpProcessCycle.CycleType;
import com.jzjy.erp.entity.ErpProcessInstance;
import com.jzjy.erp.entity.ErpProcessInstancePointer;
import com.jzjy.erp.entity.ErpProcessInstancePointer.Suspended;
import com.jzjy.erp.entity.ErpProcessInstancePointer.Timedgame;
import com.jzjy.erp.entity.ErpProcessRecord;
import com.jzjy.erp.entity.ErpProcessRecord.RecordStatus;
import com.jzjy.erp.entity.ErpProcessStatus;
import com.jzjy.erp.entity.ErpProcessStatus.ProcessStatus;
import com.jzjy.erp.entity.ErpProcessStatus.PsType;
import com.jzjy.erp.utils.DateUtil;
import com.jzjy.erp.utils.JsonUtil;

/**
 * Description:流程执行类 
 * @version 1.0  2017-7-18 上午9:43:52 王斌 (wangb@unimlink.com) created
 */
public class ExecutionService {
	
	Logger logger = Logger.getLogger(ProcessService.class);
	
	@Resource
	ProcessService processService;
	@Resource
	ProcessdefService processdefService;
	@Resource
	ProcessInstanceService processInstanceService;
	@Resource
	ProcessInstancePointerService processInstancePointerService;
	@Resource
	ProcessRecordService processRecordService;
	@Resource
	ProcessCycleService processCycleService;
	@Resource
	ProcessStatusService processStatusService;
	
	/************************************* 流程相关 ********************************************/

	/**
	 * Description: 根据流程状态获得它的前面的流程过程
	 * @Version 1.0 2017-7-5 上午11:44:09 王斌(wangb@unimlink.com) 创建
	 * @param process
	 * @return
	 */
	public List<ErpProcess> getBeforeProcess(ErpProcess process) {
		List<ErpProcess> list = new ArrayList<ErpProcess>();
		if (SubProcesses.No.getValue() == process.getSubProcesses()){ // 非子流程
			return this.processService.findBeforeProcessById(process.getId(), process.getProcessDefId(), false);
		}else {
			ErpProcess parentProcess = this.getPreFirstSupserProcess(process.getId());
			list = this.processService.findBeforeProcessById(parentProcess.getId(), parentProcess.getProcessDefId(), true);
			Integer id = process.getPreProcess();
			//获取子流程的所有前面的流程
			while (true) {
				ErpProcess e = this.processService.findById(id);
				if (e == null)
					break;
				if (process.getSubProcesses() == 1) {
					id = e.getPreProcess();
					list.add(e);
				} else
					break;
			}
		}
		return list;
	}
	
	/**
	 * Description: 往前获得第一个上级节点
	 * @Version 1.0 2017-7-4 下午7:09:30 王斌(wangb@unimlink.com) 创建
	 * @param process_id
	 * @return
	 */
	public ErpProcess getPreFirstSupserProcess(Integer process_id) {
		ErpProcess process = this.processService.findById(process_id);
		if (SubProcesses.Yes.getValue() == process.getSubProcesses())
			return this.getPreFirstSupserProcess(process.getPreProcess());
		return process;
	}

	/**
	 * Description: 往后获得最后一个上级节点
	 * @Version 1.0 2017-7-4 下午7:10:27 王斌(wangb@unimlink.com) 创建
	 * @param process_id
	 * @return
	 */
	public ErpProcess getAfterFirstSupserProcess(Integer processId) {
		ErpProcess process = this.processService.findById(processId);
		if (SubProcesses.Yes.getValue() == process.getSubProcesses())
			return this.getPreFirstSupserProcess(process.getNextProcess());
		return process;
	}
	
	/**
	 * Description: 返回当前的流程状态
	 * @Version 1.0 2017-7-5 下午3:05:00 王斌(wangb@unimlink.com) 创建
	 * @param instanceId
	 * @return
	 */
	public ErpProcess findCurrentProcessStatus(String instanceId) {
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		if (processInstance == null) {
			return null;
		}
		// 是结束流程
		if (Ended.Yes.getValue() == processInstance.getEnded())
			return null;
		
		return this.processService.findProcessByProcessInstanceId(processInstance.getId());
	}

	/**
   * Description: 根据流程实例判断流程是否结束
   * @Version 1.0 2017-7-5 下午3:24:54 王斌(wangb@unimlink.com) 创建
   * @param instance_id
   * @return
   */
	public boolean isEnd(String instanceId) {
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		if (processInstance != null)
			return (Ended.Yes.getValue() == processInstance.getEnded());
		return false;
	}

  /************************************** 流程实例相关 *******************************************/
  
  /**
   * Description: 启动一个新的流程
   * @Version 1.0 2017-7-5 上午11:45:56 王斌(wangb@unimlink.com) 创建
   * @param defid
   * @param instanceid
   */
	public synchronized boolean startProcess(Integer defid, String instanceId,Integer userid)throws Exception {
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(defid, instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance != null) {
			logger.error("startProcess : the instance already exists in the process");
			return false;
		}
		Integer processInstanceId = this.processInstanceService.createInstance(defid, instanceId);
		if(processInstanceId <= 0){
			logger.error("startProcess : create processInstance fail.");
			return false;
		}
		
		// 设定初始指针
		ErpProcess process = this.processService.findStartProcess(defid);
		if (process != null) {
			// 如果流程不结束
			if (Ended.No.getValue() == process.getEnded()) {
				List<ErpProcess> list = this.processService.findNextProcess(process);
				logger.error("[" + process.getId() + "][" + process.getName() + "] 开始处理的流程数目:" + list.size());
				
				for (ErpProcess obj : list) {
					processInstancePointerService.createPointer(instanceId,obj.getId(), processInstanceId,getWarntime(instanceId, obj.getId()),true);
					//创建流程过程记录
					String str = "启动流程["+process.getName()+"]";
					String nextstr = "启动流程["+process.getName()+"],下一流程["+obj.getName()+"]";
					processRecordService.createProcessRecord(process.getId(), instanceId, userid,RecordStatus.Normal,true,str);
					processRecordService.createProcessRecord(obj.getId(), instanceId, null,RecordStatus.Normal,false,nextstr);
					this.processStatusService.createProcessStatus(instanceId, obj.getId(), ProcessStatus.Normal,PsType.Process);
					this.processStatusService.createProcessStatus(instanceId, process.getProcessDefId(), ProcessStatus.Normal,PsType.ProcessDef);
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Description: 暂停流程
	 * @Version 1.0 2017-7-7 上午10:44:41 王斌(wangb@unimlink.com) 创建
	 * @param isRoutine 是否常规暂停流程
	 * @return
	 */
	public synchronized boolean suspendProcess(String instanceId,String reason,Integer userid,boolean isRoutine) throws Exception{
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("suspendProcess : processInstance is null.");
			return false;
		}
		List<ErpProcessInstancePointer> list = this.processInstancePointerService.findProcessInstancePointer(processInstance.getId());
		if(list == null || list.isEmpty()){
			logger.error("suspendProcess : erpProcessInstancePointer is null.");
			return false;
		}
		//当前流程是否暂停的
		ErpProcessInstancePointer pointer = list.get(0);
		if(Suspended.Yes.getValue() == pointer.getSuspended()){
			logger.error("suspendProcess : the process has been paused.");
			return false;
		}
		ErpProcess currProcess = this.processService.findById(pointer.getProcessId()); 
		if (currProcess == null) {
			return false;
		}
		
		pointer.setSuspendtime(DateUtil.getSysDateTime());//记录暂停时间
		pointer.setSuspended(Suspended.Yes.getValue());
		int num = this.processInstancePointerService.updatePointer(pointer);
		if(num > 0){
			//记录时间状态
			this.processRecordService.updateEndtime(currProcess.getId(), instanceId, userid);
//			String str = "暂停流程[当前:"+currProcess.getName()+"]";
			reason += "["+currProcess.getName()+"]";
			reason = reason.replaceAll(",", "，");
			this.processRecordService.createProcessRecord(currProcess.getId(), instanceId, null,RecordStatus.Suspend, false,reason);
			//记录项目流程状态
			if(isRoutine){
				this.checkStatus(instanceId, currProcess.getId(),pointer.getWarntime());
			}else{
				this.processStatusService.updateProcessStatus(instanceId, currProcess.getId(), ProcessStatus.Complete,PsType.Process);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 恢复暂停流程
	 * @Version 1.0 2017-7-7 下午1:14:41 王斌(wangb@unimlink.com) 创建
	 */
	public synchronized boolean recoveryProcess(String instanceId,Integer userid) throws Exception{
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("recoveryProcess : processInstance is null.");
			return false;
		}
		List<ErpProcessInstancePointer> list = this.processInstancePointerService.findProcessInstancePointer(processInstance.getId());
		if(list == null || list.isEmpty()){
			logger.error("recoveryProcess : erpProcessInstancePointer is null.");
			return false;
		}
		//当前流程已恢复
		ErpProcessInstancePointer pointer = list.get(0);
		if(Suspended.No.getValue() == pointer.getSuspended()){
			logger.error("recoveryProcess : The pause process has been restored.");
			return false;
		}
		
		ErpProcess currProcess = this.processService.findById(pointer.getProcessId()); 
		if (currProcess == null) {
			return false;
		}
		
		// 记录流程记录
		pointer.setWarntime(getWarntime(instanceId, currProcess.getId()));
		pointer.setSuspended(Suspended.No.getValue());
		int num = this.processInstancePointerService.updatePointer(pointer);
		if(num > 0){
			//记录流程总用时
			this.saveSuspendProcessTime(processInstance, pointer);
			
			this.processRecordService.updateEndtime(currProcess.getId(), instanceId, userid);
			String str = "暂停恢复流程[当前:"+currProcess.getName()+"]";
			this.processRecordService.createProcessRecord(currProcess.getId(), instanceId, null,RecordStatus.Normal, false,str);
			
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 通过流程,计时
	 * @Version 1.0 2018-4-17 下午4:03:53 王斌(wangb@unimlink.com) 创建
	 */
	public synchronized boolean completProcess(String instanceId, Integer userid) throws Exception {
		return this.completProcess(instanceId, userid, true);
	}
	
	/**
	 * Description: 通过流程(执行到下一步流转)
	 * @Version 1.0 2017-7-6 下午3:55:18 王斌(wangb@unimlink.com) 创建
	 * @param instanceId
	 */
	public synchronized boolean completProcess(String instanceId, Integer userid,boolean timedgame) throws Exception {
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("executeProcess : processInstance is null.");
			return false;
		}
		
		ErpProcess currentProcess = this.processService.findProcessByProcessInstanceId(processInstance.getId()); // 当前流程
		if (currentProcess == null) {
			logger.error("completProcess : currentProcess is null.");
			return false;
		}
		ErpProcess nextProcess = null;
		if (currentProcess.getNextProcess() != null) {
			nextProcess = this.processService.findBySequence(currentProcess.getNextProcess());
		}
		// 不存在后续流程 则返回
		if (nextProcess == null) {
			logger.error("completProcess : nextProcess is null.");
			return false;
		}
		
		logger.error(nextProcess.getEnded() + "   nextProcess >>>>> " + nextProcess.getName() + "  " + nextProcess.getId());
		// 如果结束 则修改实例状态
		if (Ended.Yes.getValue() == nextProcess.getEnded()) {
			processInstance.setEnded(Ended.Yes.getValue());
			processInstance.setEndtime(DateUtil.getSysDateTime());
			this.processInstanceService.updateInstance(processInstance);
			this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
			
			// 当前流程记录完成时间,并创建下一流程的记录
			this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
			String str = "结束流程[当前:"+currentProcess.getName()+"][下一流程:"+nextProcess.getName()+"]";
			this.processRecordService.createProcessRecord(nextProcess.getId(), instanceId, userid,RecordStatus.Normal, true,str);
			//记录项目流程状态,记录当前完成,运算下一步
			this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete,PsType.Process);
			this.endProcessStatus(instanceId,currentProcess.getProcessDefId(), nextProcess.getId());
			return true;
		}
		
		// 实例指针转换
		List<ErpProcessInstancePointer> pointerList = this.processInstancePointerService.findProcessInstancePointer(processInstance.getId());
		if (pointerList == null) {
			logger.error("completProcess : rrpProcessInstancePointer is null.");
			return false;
		}
		// 单一流程
		int size = pointerList.size();
		if (size == 1) {
			// 删除原来的指针指向 生成新的
			this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
			String warntime = getWarntime(instanceId, nextProcess.getId());
			this.processInstancePointerService.createPointer(instanceId,nextProcess.getId(), processInstance.getId(),warntime,timedgame);
			
			// 当前流程记录完成时间,并创建下一流程的记录
			this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
			String str = "通过流程[当前:"+currentProcess.getName()+"][下一流程:"+nextProcess.getName()+"]";
			//logger.error("000000000000000000000000000000000 completProcess >>>>> instanceId : " + instanceId + " --- processId:" + nextProcess.getId());
			this.processRecordService.createProcessRecord(nextProcess.getId(), instanceId, null,RecordStatus.Normal, false,str);
			//记录项目流程状态,记录当前完成,运算下一步
			this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete,PsType.Process);
			this.checkStatus(instanceId, nextProcess.getId(),warntime);
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 指派流程
	 * @Version 1.0 2017-8-19 下午2:42:26 王斌(wangb@unimlink.com) 创建
	 */
	public synchronized boolean appointProcess(String instanceId, Integer processId,Integer userid) throws Exception{
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("appointProcess : processInstance is null.");
			return false;
		}
		ErpProcess currentProcess = this.processService.findProcessByProcessInstanceId(processInstance.getId()); // 当前流程
		ErpProcess nextProcess = this.processService.findById(processId); // 指定要流转的流程
		if (currentProcess == null) {
			logger.error("appointProcess : currentProcess is null.");
			return false;
		}
		if (nextProcess == null) {
			logger.error("appointProcess : reworkProcess is null.");
			return false;
		}
		// 删除原来的指针指向 生成新的
		this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
		String warntime = getWarntime(instanceId, nextProcess.getId());
		this.processInstancePointerService.createPointer(instanceId,nextProcess.getId(), processInstance.getId(), warntime,true);
		
		//记录指派流程
		this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
		String str = "指派流程[当前:"+currentProcess.getName()+"][下一流程:"+nextProcess.getName()+"]";
		this.processRecordService.createProcessRecord(nextProcess.getId(), instanceId, null,RecordStatus.Normal, false,str);
		//记录项目流程状态,记录当前完成,运算下一步
		this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete,PsType.Process);
		this.checkStatus(instanceId, nextProcess.getId(),warntime);
		return true;
	} 
	
	/**
	 * Description: 返工流程
	 * @Version 1.0 2017-7-6 上午9:41:40 王斌(wangb@unimlink.com) 创建
	 * @param instanceId 当前实例id
	 * @param processId
	 * @throws Exception
	 */
	public synchronized boolean reworkProcess(String instanceId, Integer processId,String reason,Integer userid) throws Exception {
		
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("reworkProcess : processInstance is null.");
			return false;
		}
		// 判断驳回的情况
		ErpProcess currentProcess = this.processService.findProcessByProcessInstanceId(processInstance.getId()); // 当前流程
		ErpProcess reworkProcess = this.processService.findById(processId); // 要驳回的流程
		if (currentProcess == null) {
			logger.error("reworkProcess : currentProcess is null.");
			return false;
		}
		if (reworkProcess == null) {
			logger.error("reworkProcess : reworkProcess is null.");
			return false;
		}
		if (reworkProcess.getSequence() > currentProcess.getSequence()) {
			logger.error("reworkProcess : rework the process after the current process.");
			return false;
		}
		// 删除原来的指针指向 生成新的
		this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
		String warntime = getWarntime(instanceId, reworkProcess.getId());
		this.processInstancePointerService.createPointer(instanceId,reworkProcess.getId(), processInstance.getId(), warntime,true);
		//记录返工流程
		this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
		String msg = "返工流程[当前:"+currentProcess.getName()+"][下一流程:"+reworkProcess.getName()+"]";
		if(StringUtils.isBlank(reason))
			reason = msg;
		
		reason = reason.replaceAll(",", "，");
		this.processRecordService.createProcessRecord(reworkProcess.getId(), instanceId, null,RecordStatus.Rework, false,reason,currentProcess.getId());
		//记录项目流程状态,记录当前完成,运算下一步
		this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete,PsType.Process);
		this.checkStatus(instanceId, reworkProcess.getId(),warntime);
		logger.info(msg);
		return true;
	}
	
	/**
	 * Description: 失败流程
	 * @Version 1.0 2017-7-15 上午10:51:34 王斌(wangb@unimlink.com) 创建
	 */
	public synchronized boolean failProcess(String instanceId, Integer processId,String reason,Integer userid) throws Exception {
		
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("failProcess : processInstance is null.");
			return false;
		}
		ErpProcess currentProcess = this.processService.findCurrProcessByInstanceId(instanceId); // 当前流程
		if (currentProcess == null) {
			logger.error("failProcess : currentProcess is null.");
			return false;
		}
		if(processId != null){
			ErpProcess nextProcess = this.processService.findById(processId); // 失败前的流程
			if (nextProcess == null) {
				logger.error("failProcess : nextProcess is null.");
				return false;
			}
		}
		processInstance.setEnded(Ended.Yes.getValue());
		processInstance.setEndtime(DateUtil.getSysDateTime());
		this.processInstanceService.updateInstance(processInstance);
		this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
		
		// 当前流程记录完成时间,并创建下一流程的记录
		this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
		this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete,PsType.Process);
		if(processId != null){
			reason = reason.replaceAll(",", "，");
			this.processRecordService.createProcessRecord(processId, instanceId, null,RecordStatus.Fail, true,reason);
			this.endProcessStatus(instanceId,currentProcess.getProcessDefId(), processId);
		}
		logger.info("失败流程[当前:"+currentProcess.getName()+"][下一流程:"+processId+"]");
		return true;
	}
	
	/**
	 * Description: 结束流程
	 * @Version 1.0 2017-12-11 下午3:14:26 王斌(wangb@unimlink.com) 创建
	 */
	public synchronized boolean endProcess(String instanceId,Integer userid,String reason) throws Exception {
		
		ErpProcessInstance processInstance = this.processInstanceService.findProcessInstance(instanceId);
		// 如果不存在流程实例 表示是新增
		if (processInstance == null) {
			logger.error("endProcess : processInstance is null.");
			return false;
		}
		ErpProcess currentProcess = this.processService.findProcessByProcessInstanceId(processInstance.getId()); // 当前流程
		if (currentProcess == null) {
			logger.error("endProcess : currentProcess is null.");
			return false;
		}
		ErpProcess endProcess = this.processService.findEndProcess(currentProcess.getProcessDefId()); // 结束流程
		if (endProcess == null) {
			logger.error("endProcess : endProcess is null.");
			return false;
		}
		
		processInstance.setEnded(Ended.Yes.getValue());
		processInstance.setEndtime(DateUtil.getSysDateTime());
		this.processInstanceService.updateInstance(processInstance);
		this.processInstancePointerService.deleteProcessInstancePointer(processInstance.getId());
		
		// 当前流程记录完成时间,并创建下一流程的记录
		this.processRecordService.updateEndtime(currentProcess.getId(), instanceId, userid);
		String str = "结束流程[当前:" + currentProcess.getName() + "][下一流程:" + endProcess.getName() + "]";
		if(StringUtils.isNotBlank(reason))
			str = reason + str ;
		
		this.processRecordService.createProcessRecord(endProcess.getId(), instanceId, userid, RecordStatus.Normal, true, str);
		// 记录项目流程状态,记录当前完成,运算下一步
		this.processStatusService.updateProcessStatus(instanceId, currentProcess.getId(), ProcessStatus.Complete, PsType.Process);
		this.endProcessStatus(instanceId, currentProcess.getProcessDefId(), endProcess.getId());
		return true;
	}

	/**
	 * Description: 获取告警时间
	 * @Version 1.0 2017-7-8 下午12:55:30 王斌(wangb@unimlink.com) 创建
	 * 
	 */
	private String getWarntime(String instanceId, Integer processId) throws Exception {
		String warntime = "";
		ErpProcessCycle pcycle = processCycleService.findCycleById(CycleType.Process, processId, instanceId);
		if (pcycle == null)
			return warntime;
		
		ErpProcess process = this.processService.findById(processId);
		if (process == null)
			return warntime;
		
		Integer cycle = 0;
		List<ErpProcessRecord> records = processRecordService.findProcessRecord(instanceId, processId, false);
		if (records == null || records.isEmpty()) {
			cycle = (pcycle.getCycle() * 60);
			warntime = DateUtil.addMinute(cycle.intValue());
		} else {
			// 告警时间 = 当前时间 + （流程周期 - （非暂停状态结束时间 - 非暂停状态开始时间） ）
			double already = 0; // 已耗时，分钟 含节假日
			Integer holidaytime = 0;// 已耗时,分钟 只有节假日
			for (ErpProcessRecord record : records) {
				Date enddate = null;
				if (StringUtils.isNotBlank(record.getEndtime())) {
					enddate = DateUtil.str2DateTime(record.getEndtime());
				} else {
					enddate = new Date();
				}
				already += Double.valueOf(DateUtil.minuteBetween(enddate, DateUtil.str2DateTime(record.getStarttime())));
				holidaytime += addWarntimeByHoliday(DateUtil.str2DateTime(record.getStarttime()).getTime(), enddate.getTime());
			}
			// 已耗时 = 总已耗时 (含节假日) - 只节假日的耗时
			already = already - holidaytime;
			Double cycle_ = (pcycle.getCycle() * 60) - already;
			cycle = cycle_.intValue();
			// 重新获取告警时间
		}
		String date = DateUtil.getSysDateTime(); // 当前时间
		String starttime = isContainsHoliday(date); // 获取起始时间
		// 当前时间是节假日，那流程开始时间从0点开始
		if (!date.equals(starttime))
			starttime = starttime.split(" ")[0] + " 00:00:00";
		
		if(cycle < 0) cycle = 0;
		
		//全日制 7 * 24
		warntime = DateUtil.addMinute(DateUtil.str2DateTime(starttime), cycle.intValue()); // 正常告警时间
		String endtime = isContainsFestival(warntime); // 查看告警时间是否是节日
		Integer holiday = addWarntimeByFestival(DateUtil.str2DateTime(starttime).getTime(), DateUtil.str2DateTime(endtime).getTime()); // 加上节日的时间
		if(DepaCycleType.Normal.getValue() == process.getDepaCycleType()){
			//正常工作日 955 TODO 暂去掉
//			logger.error(">>>>>>>>>>>>> starttime : " + starttime + " cycle:" + cycle);
//			warntime = this.deptWorkTime(starttime, cycle);
//			logger.error(">>>>>>>>>>>>> warntime : " + warntime);
			endtime = isContainsHoliday(endtime); // 查看告警时间是否是假日
			holiday += addWarntimeByHoliday(DateUtil.str2DateTime(starttime).getTime(), DateUtil.str2DateTime(endtime).getTime()); // 加上假日的时间
		}
		
		cycle += holiday;
		warntime = DateUtil.addMinute(DateUtil.str2DateTime(starttime), cycle.intValue());
		
		return warntime;
	}
	
	//非全日制（7*24）的部门工作时间
	String workTime = JzjyErpConfig.getContent("dept_work_time");
	String workTimeHour = JzjyErpConfig.getContent("dept_work_hour");
	
	/**
	 * Description: 运算非全日制部门的告警时间
	 * @Version 1.0 2018-6-6 下午4:13:48 王斌(wangb@unimlink.com) 创建
	 */
	@SuppressWarnings("unused")
	private String deptWorkTime(String time,Integer cycle) throws Exception{
		
		//1、用开始时间，根据当前流程时长，和上班工作时间进行判断
		List<String> workTimeList = JsonUtil.JsonToBean(workTime, new TypeToken<List<String>>() {});
		if(workTimeList == null || workTimeList.isEmpty()) return null;
		//起始日期
		String startdate = time.split(" ")[0];
		//得出还剩下天
		//如已没有时长可用，则工作的开始时间为告警时间
		
		if(cycle < Double.parseDouble(workTimeHour)){
			String timestr = workTimeList.get(0);
			String start = timestr.split("_")[0];
			
			//当前 
			String starttime = startdate + " " + start;
			
			long timelong = DateUtil.str2DateTime(time).getTime();
			long starttimelong = DateUtil.str2DateTime(starttime).getTime();
			if(timelong < starttimelong){
				return starttime;
			}else{
				time = DateUtil.addHour(DateUtil.str2DateTime(time),cycle);
				//TODO 1、当前时间 加上时长 
				//TODO 2、比较下告警时间是否在当天的工作时间内
				//TODO 3、如果在 用工作时间集合比较，验证上午后，同时用下午的开始时间和上午的结束时间，
				//在进行比较，看告警时间是否在午休时间，如果在用告警时间减去上午结束时间，然后放在下午
				//TODO 4、如果不在当天，用告警时间减去下班时间得出剩余时长，进行后续运算
				//TODO 5、当前时间加一天，时间从第二天的上午上班时间开始，加上剩余时长
				//TODO 2、在验证一遍2、3、4
				//TODO 注 ：以上写的过于负责，是含午休时间的工作时间
				long time1 = DateUtil.str2DateTime(time).getTime();
				Date time2 = DateUtil.str2DateTime(startdate + " " + workTimeList.get(0).split("_")[0]);
				long time2long = time2.getTime();
				long time3 = DateUtil.str2DateTime(startdate + " " + workTimeList.get(0).split("_")[1]).getTime();
				//告警时间在工作时间内
				if(time1 >= time2long && time1 <= time3){
					return time;
				}else if(time1 > time3){
					time = DateUtil.addDay(DateUtil.str2DateTime(starttime),1,null);
					long time4 = (time1 - time3)  / 1000 / 60;
					return DateUtil.addMinute(DateUtil.str2DateTime(time),Long.valueOf(time4).intValue());
				}
			}
		}
		
		cycle = (cycle * 60); //按分钟算
		while (cycle > 0) {
			//TODO 可以时长先减去整天数,然后在进行不满一天的时间运算
			String datetime = deptWorkTimeFor(workTimeList, time, cycle);
			if(StringUtils.isNotBlank(datetime)){
				if(datetime.startsWith("cycle_")){
					cycle = Integer.parseInt(datetime.replaceAll("cycle_", ""));
				}else{
					return datetime;
				}
			}
			time = DateUtil.addDay(DateUtil.str2DateTime(time),1,null);
		}
		return null;
	}
	
	/**
	 * Description: 运算非全日制部门的告警时间
	 * @Version 1.0 2018-6-7 下午5:47:04 王斌(wangb@unimlink.com) 创建
	 */
	private String deptWorkTimeFor(List<String> workTimeList,String time,Integer cycle) throws Exception{
		String startdate = time.split(" ")[0];
		
		String timestr = workTimeList.get(0);
		String start = timestr.split("_")[0];
		String end = timestr.split("_")[1];
		
		//当前
		String starttime = startdate + " " + start;
		String endtime = startdate + " " + end;
		
 		long minute = DateUtil.minuteBetween(DateUtil.str2DateTime(endtime), DateUtil.str2DateTime(starttime));
		if(cycle > minute){
			//不在该工作时间内，减去后，原始时间增加一天
			cycle = (int) (cycle - minute);
		}else{
			//如得出告警时间，超出工作时间，加上剩余分钟数
			time = DateUtil.addMinute(DateUtil.str2DateTime(time),cycle);
			
			long time1 = DateUtil.str2DateTime(time).getTime();
			Date time2 = DateUtil.str2DateTime(startdate + " " + workTimeList.get(0).split("_")[0]);
			long time2long = time2.getTime();
			long time3 = DateUtil.str2DateTime(startdate + " " + workTimeList.get(0).split("_")[1]).getTime();
			//告警时间在工作时间内
			if(time1 >= time2long && time1 <= time3){
				return time;
			}else if(time1 > time3){
				time = DateUtil.addDay(DateUtil.str2DateTime(starttime),1,null);
				long time4 = (time1 - time3)  / 1000 / 60;
				return DateUtil.addMinute(DateUtil.str2DateTime(time),Long.valueOf(time4).intValue());
			}
		}
		return "cycle_"+cycle;
	}
	
	/**
	 * Description: 验证流转到的流程 是否超时,并修改状态 
	 * @Version 1.0 2017-7-18 上午10:50:46 王斌(wangb@unimlink.com) 创建
	 */
	private void checkStatus(String instanceId, Integer processId,String warntime) throws Exception{
		
//		List<ErpProcessRecord> records = this.processRecordService.findProcessRecord(instanceId, processId, false);
//		if (records == null || records.isEmpty()) {
//			logger.error("checkStatus : record is null.");
//			return;
//		}
		
		ErpProcessInstancePointer pointer = this.processInstancePointerService.findCurrProcessInstancePointerByInstanceId(instanceId);
		if(pointer == null){
			logger.error("ProcessAlarmJob : ["+instanceId+"] pointer is null. ");
			return;
		}
		if(Timedgame.Yes.getValue() == pointer.getTimedgame() && StringUtils.isBlank(pointer.getWarntime())){
			logger.error("ProcessAlarmJob : ["+instanceId+"] warntime is null. ");
			return;
		}
		
		ErpProcessCycle cycle = this.processCycleService.findCycleById(CycleType.Process, processId,instanceId);
		if (cycle == null) {
			logger.error("checkStatus : pcycle is null.");
			return;
		}
		
		ProcessStatus status = ProcessStatus.Normal;
		//不计时，不进行告警运算
		if(Timedgame.Yes.getValue() == pointer.getTimedgame()){
			//阀值
			Double spot = Double.parseDouble(JzjyErpConfig.getContent("process_overtime_spot")); //默认80%
			logger.error("------------------------------------ instanceId : " + instanceId + " pointer.getProcessId(): " + pointer.getProcessId()+ " --- pointer.getWarntime() : " + pointer.getWarntime() + " -- warntime:" +warntime);
			Date warndate = DateUtil.str2DateTime(warntime);  //告警时间
			long warn = warndate.getTime();  //告警时间 ,毫秒
			long curr = DateUtil.getTime();  //当前时间
			int zongCycle = cycle.getCycle() * 60;
			int earlyCycle = (int)(zongCycle * spot);  //预警时长
			int earlyLine = zongCycle - earlyCycle;  //预警线
			
			//预警时间 = 告警时间  - (总时长 - (总时长 * 预警线))
			long early = DateUtil.str2DateTime(DateUtil.addMinute(warndate,-earlyLine)).getTime();
			if(curr >= warn){//告警
				status = ProcessStatus.Overtime;
			}else if(curr >= early){//预警
				status = ProcessStatus.EarlyWarning;
			}
		}
		
//		long already = 0; // 已耗时，秒
//		long warn = pcycle.getCycle()  * 3600 ;   //告警线,秒
//		BigDecimal b1 = new BigDecimal(warn);
//    BigDecimal b2 = new BigDecimal(spot);
//		long early = b1.multiply(b2).longValue();  //预警线
//		
//		for (ErpProcessRecord record : records) {
//			Date enddate = null;
//			if(StringUtils.isNotBlank(record.getEndtime())){
//				enddate = DateUtil.str2DateTime(record.getEndtime());
//			}else{
//				enddate = new Date();
//			}
//			already += DateUtil.secondBetween(enddate, DateUtil.str2DateTime(record.getStarttime()));
//		}
//		ProcessStatus status = ProcessStatus.Normal;
//		if(already >= warn){//告警
//			status = ProcessStatus.Overtime;
//		}else if(already >= early){//预警
//			status = ProcessStatus.EarlyWarning;
//		}
		logger.error(">>>>>>>>>>>>>>>>>>> instanceId:" + instanceId + " -- status :" + status.name() + "  -- is Timedgame ："+ (pointer.getTimedgame()) + " -- warntime:"+pointer.getWarntime());
		ErpProcessStatus entity = this.processStatusService.findProcessStatus(instanceId, processId,PsType.Process);
		if(entity == null){
			this.processStatusService.createProcessStatus(instanceId, processId, status,PsType.Process);
		}else{
			this.processStatusService.updateProcessStatus(instanceId, processId, status,PsType.Process);
		}
	}
	
	/**
	 * Description: 设置结束流程状态
	 * @Version 1.0 2017-9-4 下午5:19:57 王斌(wangb@unimlink.com) 创建
	 */
	private void endProcessStatus(String instanceId,Integer defid,Integer processId) throws Exception{
		ErpProcessStatus entity = this.processStatusService.findProcessStatus(instanceId, processId,PsType.Process);
		ProcessStatus status = ProcessStatus.Complete;
		if(entity == null){
			this.processStatusService.createProcessStatus(instanceId, processId, status,PsType.Process);
		}else{
			this.processStatusService.updateProcessStatus(instanceId, processId, status,PsType.Process);
		}
		this.processStatusService.updateProcessStatus(instanceId, defid, status,PsType.ProcessDef);
	}
	
	/**
	 * Description: 记录流程总用时
	 * @Version 1.0 2017-9-28 上午10:09:45 王斌(wangb@unimlink.com) 创建
	 */
	private void saveSuspendProcessTime(ErpProcessInstance processInstance,ErpProcessInstancePointer pointer) throws Exception{
	//暂停流程用时 = 用当前时间 - 暂停开始时间
		Long usedTime = DateUtil.secondBetween(new Date(), DateUtil.str2DateTime(pointer.getSuspendtime()));
		Integer spt = (processInstance.getSuspendProcessTime() == null)?0:processInstance.getSuspendProcessTime();
		//流程暂停总用时 = 以往暂停流程用时 + 当前暂停流程用时
		processInstance.setSuspendProcessTime(spt + usedTime.intValue());
		this.processInstanceService.updateInstance(processInstance);
	}
	
	/**
	 * Description: 查看流程时长内是否存在节日，并返回小时数
	 * @Version 1.0 2018-1-26 上午10:50:11 王斌(wangb@unimlink.com) 创建
	 */
	public Integer addWarntimeByFestival(long start,long end) throws Exception{
		List<Long> holidays = getFestivalTime();
		Integer result = 0;
		for (Long holiday : holidays) {
			
			if(holiday >= start && holiday <= end){
				result ++;
			}
		}
		if(result > 0)
			result = result * 24 * 60; //得出分钟
		
		return result;
	}
	
	/**
	 * Description: 查看流程时长内是否存在假日，并返回小时数
	 * @Version 1.0 2018-1-26 上午10:50:11 王斌(wangb@unimlink.com) 创建
	 */
	public Integer addWarntimeByHoliday(long start,long end) throws Exception{
		List<Long> holidays = getHolidayTime();
		Integer result = 0;
		for (Long holiday : holidays) {
			 
			if(holiday >= start && holiday <= end){
				result ++;
			}
		}
		if(result > 0)
			result = result * 24 * 60; //得出分钟
		
		return result;
	}
	
	/**
	 * Description: 查看时间是否为 节日，如果是往后瞬移直至工作日
	 * @Version 1.0 2018-1-26 下午4:02:30 王斌(wangb@unimlink.com) 创建
	 */
	private String isContainsFestival(String date) throws Exception{
		//1、查看起始时间是否节假日，如果是往后顺延至工作日0点，结束时间为顺延后时间 + 时长
		//2、(结束时间 ：起始时间 + 时长) ，查看结束时间是否节假日，如果是往后顺延至工作日
		
		LinkedHashMap<String,String> map = getFestivalForMap();
		if(map.containsKey(date.split(" ")[0])){
			String result = DateUtil.addDay(DateUtil.str2DateTime(date),1,null);
			if(map.containsKey(result.split(" ")[0])){
				return isContainsFestival(result);
			}else{
				date = result;
			}
		}
		return date;
	}
	/**
	 * Description: 查看时间是否为 假日，如果是往后瞬移直至工作日
	 * @Version 1.0 2018-1-26 下午4:02:30 王斌(wangb@unimlink.com) 创建
	 */
	private String isContainsHoliday(String date) throws Exception{
		//1、查看起始时间是否节假日，如果是往后顺延至工作日0点，结束时间为顺延后时间 + 时长
		//2、(结束时间 ：起始时间 + 时长) ，查看结束时间是否节假日，如果是往后顺延至工作日
		
		LinkedHashMap<String,String> map = getHolidayForMap();
		if(map.containsKey(date.split(" ")[0])){
			String result = DateUtil.addDay(DateUtil.str2DateTime(date),1,null);
			if(map.containsKey(result.split(" ")[0])){
				return isContainsHoliday(result);
			}else{
				date = result;
			}
		}
		return date;
	}
	
	/**
	 * Description: 获取节日的毫秒数
	 * @Version 1.0 2018-1-25 下午4:06:48 王斌(wangb@unimlink.com) 创建
	 */
	private List<Long> getFestivalTime() throws Exception{
		String calendar = JzjyErpConfig.getContent("calendar_manage_festival");
		if(StringUtils.isBlank(calendar)) return new ArrayList<Long>();
		
		List<Long> list = new ArrayList<Long>();
		LinkedHashMap<String,String> calmap = JsonUtil.JsonToBean(calendar, new TypeToken<LinkedHashMap<String,String>>() {});
		for (String key: calmap.keySet()) {
			list.add(DateUtil.str3DateTime(formatDate(key)).getTime());
		}
		return list;
	}
	
	/**
	 * Description: 获取假日的毫秒数
	 * @Version 1.0 2018-1-25 下午4:06:48 王斌(wangb@unimlink.com) 创建
	 */
	private List<Long> getHolidayTime() throws Exception{
		String calendar = JzjyErpConfig.getContent("calendar_manage_holiday");
		if(StringUtils.isBlank(calendar)) return new ArrayList<Long>();

		List<Long> list = new ArrayList<Long>();
		LinkedHashMap<String,String> calmap = JsonUtil.JsonToBean(calendar, new TypeToken<LinkedHashMap<String,String>>() {});
		for (String key: calmap.keySet()) {
			list.add(DateUtil.str3DateTime(formatDate(key)).getTime());
		}
		return list;
	}
	
	/**
	 * Description: 获取节日
	 * @Version 1.0 2018-1-26 下午5:46:58 王斌(wangb@unimlink.com) 创建
	 */
	private LinkedHashMap<String,String> getFestivalForMap() throws Exception{
		String calendar = JzjyErpConfig.getContent("calendar_manage_festival");
		if(StringUtils.isBlank(calendar)) return new LinkedHashMap<String, String>();
		LinkedHashMap<String,String> result = new LinkedHashMap<String, String>();
		LinkedHashMap<String,String> calmap = JsonUtil.JsonToBean(calendar, new TypeToken<LinkedHashMap<String,String>>() {});
		for (String key: calmap.keySet()) {
			String str = formatDate(key);
			result.put(str,str);
		}
		return result ;
	}
	/**
	 * Description: 获取假日
	 * @Version 1.0 2018-1-26 下午5:46:58 王斌(wangb@unimlink.com) 创建
	 */
	private LinkedHashMap<String,String> getHolidayForMap() throws Exception{
		String calendar = JzjyErpConfig.getContent("calendar_manage_holiday");
		if(StringUtils.isBlank(calendar)) return new LinkedHashMap<String, String>();
		LinkedHashMap<String,String> result = new LinkedHashMap<String, String>();
		LinkedHashMap<String,String> calmap = JsonUtil.JsonToBean(calendar, new TypeToken<LinkedHashMap<String,String>>() {});
		for (String key: calmap.keySet()) {
			String str = formatDate(key);
			result.put(str,str);
		}
		return result ;
	}
	
	/**
	 * Description: 转换日期格式
	 * @Version 1.0 2018-1-26 上午10:25:17 王斌(wangb@unimlink.com) 创建
	 */
	private String formatDate(String date){
		String[] arr = date.split("-");
		String m = (Integer.parseInt(arr[1]) > 9) ?arr[1] : "0" + arr[1];
		String d = (Integer.parseInt(arr[2]) > 9) ?arr[2] : "0" + arr[2];
		
		return arr[0] + "-" + m + "-" + d;
	} 
	
}
