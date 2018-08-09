package com.jzjy.erp.thread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.alibaba.fastjson.JSON;
import com.jzjy.erp.Constants;
import com.jzjy.erp.config.JzjyErpConfig;
import com.jzjy.erp.entity.ErpProject;
import com.jzjy.erp.entity.ErpReport;
import com.jzjy.erp.entity.ErpReport.ReportStatus;
import com.jzjy.erp.entity.ErpReport.YiDaiCompleted;
import com.jzjy.erp.entity.ErpReport.YiDaiMarked;
import com.jzjy.erp.entity.ErpSample;
import com.jzjy.erp.entity.ErpUserbase;
import com.jzjy.erp.entity.ErpYidai;
import com.jzjy.erp.entity.ErpYidai.YidaiStatus;
import com.jzjy.erp.entity.ErpYidaiAbimeta;
import com.jzjy.erp.entity.ErpYidaiAbiresult;
import com.jzjy.erp.entity.ErpYidaiAnalysis;
import com.jzjy.erp.entity.ErpYidaiFile;
import com.jzjy.erp.entity.ErpYidaiProject;
import com.jzjy.erp.entity.ErpYidaiTask;
import com.jzjy.erp.entity.ErpYidaiTask.ResultStatus;
import com.jzjy.erp.entity.ErpYidaiTask.TaskDelay;
import com.jzjy.erp.service.AnalysisService;
import com.jzjy.erp.service.ExecutionService;
import com.jzjy.erp.service.ProcessService;
import com.jzjy.erp.service.ProjectService;
import com.jzjy.erp.service.ReportAuditService;
import com.jzjy.erp.service.ReportMakeService;
import com.jzjy.erp.service.SampleService;
import com.jzjy.erp.service.SequencingJudgeService;
import com.jzjy.erp.service.YidaiAbimetaService;
import com.jzjy.erp.service.YidaiAbiresultService;
import com.jzjy.erp.service.YidaiAnalysisService;
import com.jzjy.erp.service.YidaiFileService;
import com.jzjy.erp.service.YidaiProjectService;
import com.jzjy.erp.service.YidaiReportIssuedService;
import com.jzjy.erp.service.YidaiService;
import com.jzjy.erp.service.YidaiTaskService;
import com.jzjy.erp.utils.CompressionZip;
import com.jzjy.erp.utils.DateUtil;
import com.jzjy.erp.utils.ExcelUtil;
import com.jzjy.erp.utils.FileUtil;
import com.jzjy.erp.utils.JsonUtil;
import com.jzjy.erp.utils.SessionUtil;
import com.jzjy.erp.utils.StringHelper;
import com.jzjy.erp.utils.UnpackZip;

/**
 * Description: 家系报告派工分析
 * @Version 1.0 2017-9-15 下午5:24:17 王斌(wangb@unimlink.com) 创建
 */
@SuppressWarnings({"unchecked","rawtypes","unused"})
public class YidaiFamilyReportAnalysis implements Runnable{

	Logger logger = Logger.getLogger(YidaiFamilyReportAnalysis.class);
	
	YidaiService yidaiService = Constants.getService(YidaiService.class);
	YidaiReportIssuedService yidaiReportIssuedService = Constants.getService(YidaiReportIssuedService.class);
	SequencingJudgeService sequencingJudgeService = Constants.getService(SequencingJudgeService.class);
	YidaiProjectService yidaiProjectService = Constants.getService(YidaiProjectService.class);
	ReportMakeService reportMakeService = Constants.getService(ReportMakeService.class);
	YidaiAnalysisService yidaiAnalysisService = Constants.getService(YidaiAnalysisService.class);
	YidaiFileService yidaiFileService = Constants.getService(YidaiFileService.class);
	YidaiTaskService yidaiTaskService = Constants.getService(YidaiTaskService.class);
	AnalysisService analysisService = Constants.getService(AnalysisService.class);
	YidaiAbimetaService yidaiAbimetaService = Constants.getService(YidaiAbimetaService.class);
	YidaiAbiresultService yidaiAbiresultService = Constants.getService(YidaiAbiresultService.class);
	ProcessService processService = Constants.getService(ProcessService.class);
	ExecutionService executionService = Constants.getService(ExecutionService.class);
	ReportAuditService reportAuditService = Constants.getService(ReportAuditService.class);
	ProjectService projectService = Constants.getService(ProjectService.class);
	SampleService sampleService = Constants.getService(SampleService.class);
	
	private HttpSession session;
	private List<ErpReport> reports;
	private ErpUserbase loginuser;
	private String analysisPath; // 获取保存父路径
	private String ab1Path; // 获取表中ab1文件保存的父路径
	private String calendar;
	
	public YidaiFamilyReportAnalysis(HttpSession session, List<ErpReport> reports) {
		super();
		this.session = session;
		this.reports = reports;
		this.loginuser = SessionUtil.getUserSession(session);
		this.analysisPath = JzjyErpConfig.getContent("yidaiReportIssued_analysis_path");
		this.ab1Path = JzjyErpConfig.getContent("sequencingJudge_save_ab1_path");
		this.calendar = new SimpleDateFormat("yyyyMMdd").format(new Date());
	}

	@Override
	public void run() {

//			logger.error(">>>>>>>> analysisPath : " + analysisPath);
//			logger.error(">>>>>>>> ab1Path : " + ab1Path);
//			logger.error(">>>>>>>> calendar : " + calendar);
			
			for (ErpReport report : reports) {
				try {
					List<ErpYidai> yidaiList = yidaiReportIssuedService.findYidaiByReportId(report.getId());
					if (yidaiList == null || yidaiList.isEmpty()) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai list is null.");
						return;
					}
					// 创建一代项目
					ErpYidaiProject yidaiProject = this.createProject(report);
					
					if (yidaiProject.getId() == null) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai project creation failed.");
						return;
					}
					
					// 创建一代分析
					ErpYidaiAnalysis yidaiAnalysis = this.createAnalysis(report,yidaiProject.getId());
					
					if (yidaiAnalysis.getId() == null) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai analysis creation failed.");
						return;
					}
					
					//生成文件(zip,xlsx)
					Map<String, Object> map = this.generateFiles(report,yidaiList);
					if (map.isEmpty()) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai generateFiles failed.");
						return;
					}
					// 创建一代相关文件表abi.zip数据
					ErpYidaiFile yidaiZipFile = this.createYidaiZip(report,yidaiProject.getId(), yidaiAnalysis.getId());
					if (yidaiZipFile.getId() == null) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai zip file failed.");
						return;
					}
					
					// 创建一代相关文件表abi.xlsx数据
					ErpYidaiFile yidaiXlsxFile = this.createYidaiExcel(report,yidaiProject.getId(), yidaiZipFile.getId());
					if (yidaiXlsxFile == null) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  yidai xlsx file failed.");
						return;
					}
					
					// 创建erp_yidai_task表信息
					ErpYidaiTask prepareTask = this.createPrepareAbiTask(yidaiProject.getId(), yidaiAnalysis.getId(), yidaiZipFile.getId(), yidaiXlsxFile.getId());
					if (prepareTask.getId() == null) {
						logger.error("YidaiFamilyReportAnalysis : [" + report.getReportId() + "]  create prepare abi task failed.");
						return;
					}
					String abixlsx = StringHelper.convertStr(map.get("abixlsx"));
					if (StringUtils.isBlank(abixlsx)) {
						this.failMsg(report, yidaiList, prepareTask, true, "YidaiFamilyReportAnalysis : abixlsx is null.");
						return;
					}
					List<List<Object>> excelList = ExcelUtil.getWorkBook(abixlsx, true);
					if (excelList == null || excelList.isEmpty()) {
						this.failMsg(report, yidaiList, prepareTask, true, "YidaiFamilyReportAnalysis : excelList is null.");
						return;
					}
					
					String abizip = StringHelper.convertStr(map.get("abizip"));
					// 获取zip文件并解压
					if (StringUtils.isBlank(abizip)) {
						this.failMsg(report, yidaiList, prepareTask, true, "YidaiFamilyReportAnalysis : abizip is null.");
						return;
					}
					logger.error(">>>>>>>>>>>>>> abizip : " + abizip);
					// 解压后目录
					String extract = abizip.replace("abi.zip", "");
					logger.error(">>>>>>>>>>>>>> extract : " + extract);
					UnpackZip.unzip(abizip, extract);
					//by wangbin 解压不需要此目录，解压后需要 --! 
					extract += "abi";
					
					// 获取解压后的文件夹中所有内容
					File abiDirectory = FileUtil.newFile(extract);
					if (!abiDirectory.exists() || abiDirectory.listFiles() == null) {
						this.failMsg(report, yidaiList, prepareTask, true, "YidaiFamilyReportAnalysis : abizip is null.");
						return;
					}
					
					// 验证一代分析与ab1文件是否匹配
					Map<String, Integer> checkMap = this.check(yidaiAnalysis.getId(), abiDirectory, excelList);
					if (checkMap.isEmpty()) {
						this.failMsg(report, yidaiList, prepareTask, true, "YidaiFamilyReportAnalysis : checkMap is null.");
						return;
					}
					
					// 比较excel文件中的项目名和ab1文件中的项目名是否匹配
					if (checkMap.size() != excelList.size()) {
						String msg = "YidaiFamilyReportAnalysis : validation fail. checkSzie[" + checkMap.size() + "]-excelSize[" + excelList.size() + "]";
						this.failMsg(report, yidaiList, prepareTask, true, msg);
						return;
					}
					
					// 创建abi结果
					ErpYidaiAbiresult result = this.createAbiresult(report,yidaiAnalysis.getId());
					
					// 修改任务结果
					prepareTask = this.updateTaskForResult(report,prepareTask, ResultStatus.Finished, true, "");
					
					// 执行命令
					ErpYidaiTask task = this.executeCommand(report,excelList, checkMap, yidaiAnalysis);
					if (task != null ) {
						report.setAnalysisId(yidaiAnalysis.getId());
						report.setUrl("yidaiReportMake/toReportMaking?id=" + report.getId() + "&analysisId=" + yidaiAnalysis.getId() + "&taskId=" + task.getId());
					}
					this.modifyYidaisAndExecuProcess(report, yidaiList, "success");
					
					logger.error(">>>>>>>>>>>>>>>>>>>>>>>> del : " + abiDirectory.delete());
				} catch (Exception e) {
					report.setYidaiMarked(YiDaiMarked.No.getValue());
					report.setUpdated(DateUtil.getSysDateTime());
					int num = reportMakeService.updateReport(report);
					e.printStackTrace();
				}
			}
	}
	
	/**
	 * Description: 设置失败消息
	 * @Version 1.0 2017-9-15 下午3:59:16 王斌(wangb@unimlink.com) 创建
	 */
	private void failMsg(ErpReport report, List<ErpYidai> list, ErpYidaiTask entity, boolean prepareAbi, String msg) throws Exception {
		logger.error(msg);
		this.updateTaskForResult(report,entity, ResultStatus.Failed, prepareAbi, msg);
		this.modifyYidaisAndExecuProcess(report, list, msg); // 修改信息
	}
	
	/**
	 * Description: 设置执行命令消息
	 * @Version 1.0 2017-9-15 下午5:21:15 王斌(wangb@unimlink.com) 创建
	 * @param status 状态 fail，success
	 * @param msg 消息
	 * @return
	 */
	private Map<String, String> setCommandMsg(String status, String msg) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("status", status);
		map.put("msg", msg);
		return map;
	}
	
	/**
	 * Description: 执行命令 
	 * @Version 1.0 2017-9-15 下午5:24:17 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiTask executeCommand(ErpReport report,List<List<Object>> excelList, Map<String, Integer> checkMap, ErpYidaiAnalysis analysis) throws Exception {
		// 执行命令
		ErpYidaiTask entity = new ErpYidaiTask();
		entity.setType("analysis_abi");
		entity.setCreated(DateUtil.getSysDateTime());
		entity.setParameters("{\"inputs\": \"" + report.getReportId() + "\"}");
		//创建任务
		entity.setAnalysisId(analysis.getId());
		entity.setProjectId(analysis.getProjectId());
		entity.setUserId(loginuser.getId());
		entity.setDelay(TaskDelay.Yes.getValue());
		Integer yidaiTaskId = this.yidaiTaskService.createYidaiTask(entity);
		if(yidaiTaskId == null || yidaiTaskId <= 0){
			logger.error("executeCommand : createYidaiTask fail.");
			return null;
		}
		
		List<Map> list = new ArrayList<Map>();
		List<Map> error = new ArrayList<Map>();
		int index = 0;
		for (List<Object> row : excelList) {
			// 当前一代项目路径
			File outdir = FileUtil.newFile(StringHelper.convertStr(row.get(14))); // outdir
			File ab1File = FileUtil.newFile(StringHelper.convertStr(row.get(15))); // abifile
			String yidaiProjectId = StringHelper.convertStr(row.get(4));
			
			ErpYidaiAbimeta abimeta = yidaiAbimetaService.findAbimetaByFamilyIdAndIndividualId(yidaiProjectId);
			if (abimeta == null) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : abimeta is null."));
				continue;
			}
			ErpYidai yidai = this.sequencingJudgeService.findByYidaiId2(yidaiProjectId);
			if (yidai == null) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : yidai is null."));
				continue;
			}
			String yidaiId = yidai.getYidaiId();
			String projectId = yidaiId.substring(0,yidaiId.lastIndexOf("-"));
			ErpSample sample = this.sampleService.findByProjectId(projectId);
			if(sample == null){
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : sample is null."));
				continue;
			}
			
			
			Map map = new LinkedHashMap();
			map.put("chr_pos", row.get(10));
			map.put("status", "fail");
			map.put("gene", row.get(5));
			map.put("transctipt_id", row.get(5));
			map.put("aa_change", row.get(9));
			map.put("depth", row.get(11));
			map.put("subject_name", row.get(2));
			map.put("exon_id", row.get(7));
			map.put("family_id", row.get(1));
			map.put("individual_name", row.get(2));
			map.put("nuc_change", row.get(8));
			map.put("project_id", row.get(4));
			map.put("abi_id", checkMap.get(row.get(4)));
			map.put("hom_het", row.get(12));
//			String outdir_ = StringHelper.convertStr(row.get(14)).replaceAll(JzjyMnConfig.getContent("yidaiReportIssued_analysis_path"), JzjyMnConfig.getContent("yidaiReportIssued_analysis_url"));
			map.put("outdir", row.get(14));
			map.put("msg", "");
			map.put("xianzhengzhe", sample.getXianzhengzhe());
			
			//关联id
			abimeta.setTaskId(yidaiTaskId);
			abimeta.setThisprojectId(analysis.getProjectId());
			int num = this.yidaiAbimetaService.updateAbimeta(abimeta);
			if (num <= 0) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : update abimeta fail."));
				list.add(map);
				map.put("msg", "Error : update abimeta fail.");
				continue;
			}
			
			// 获取序列
			boolean flag = this.getSequence(outdir, row);
			if (!flag) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : get sequence fail."));
				map.put("msg", "Error : get sequence fail.");
				list.add(map);
				continue;
			}
			// 生成图片
			flag = this.checkAb1Quality(outdir, ab1File, row);
			if (!flag) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : check ab1 quality fail."));
				map.put("msg", "Error : check ab1 quality fail.");
				list.add(map);
				continue;
			}
			// 执行截图
			flag = this.screenshot(outdir);
//			if (!flag) {
//				error.add(setCommandMsg("fail", "executeCommand [" + yidaiProjectId + "] : screenshot fail."));
//				continue;
//			}
			// task3Id ==== analysis_abi task id
			// 获取相关abimeta表
			
			yidai.setStatus(YidaiStatus.ABIAnalysis.getValue());
			yidai.setUpdated(DateUtil.getSysDateTime());
			num = this.sequencingJudgeService.updateSequencingJudge(yidai);
			if (num <= 0) {
				error.add(setCommandMsg("fail", "[" + yidaiProjectId + "] : update yidai fail."));
				map.put("msg", "Error : update yidai fail.");
				list.add(map);
				continue;
			}
			map.put("status", "success");
			list.add(map);
			index++;
		}
		// 只要有一代数据完成
		Map abimetas = new HashMap();
		abimetas.put("abi_metas", list);
		String json = "{\"status\": \""+((index > 0) ? "success":"fail")+"\", \"errormsg\": "+ JsonUtil.ObjectToJson(error) +", \"successmsg\": " + JsonUtil.ObjectToJson(abimetas) + "}";
		
		//修改信息
		entity.setStatus(((index > 0) ? "finished":"failed"));
		entity.setResult(json);
		this.yidaiTaskService.updateYidaiTask(entity);
		return entity;
	}
	
	/**
	 * Description: 获取序列
	 * @Version 1.0 2017-9-12 下午6:31:52 王斌(wangb@unimlink.com) 创建
	 */
	private boolean getSequence(File outdir, List<Object> row) throws Exception {
		
		String[] position = StringHelper.convertStr(row.get(10)).split(":");
		
		String chrm = position[0].replaceAll("chrx", "chrX").replaceAll("chrm", "chrM");
		chrm = ("chrm".equalsIgnoreCase(chrm) ? "chrm.fa " : "hg19all.fa ");
		
		// 创建空文件
		File tempBedFile = FileUtil.newFile(outdir.getPath() + File.separatorChar + "temp.bed");
		tempBedFile.createNewFile();
		File refSeqFile = FileUtil.newFile(outdir.getPath() + File.separatorChar + "ref.seq");
		refSeqFile.createNewFile();
		
		// 向temp.bed写入数据
		boolean flag = FileUtil.updaeFileContent(tempBedFile, getTempBedFileContent(position));
		if (!flag) {
			logger.error("ParsingAb1UploadFile : writer tempBed fail.");
			return false;
		}
		
		String sequence_biodb = JzjyErpConfig.getContent("sequence_biodb");
		// 执行命令
		String command = "bedtools getfasta -fi " + sequence_biodb + chrm + " -bed " + tempBedFile.getPath() + " -fo " + refSeqFile.getPath();
		logger.error(">>>>>>>>>>> getSequence :" + command);
		return runtimeExec(command);
	}
	
	/**
	 * Description:检测ab1质量 
	 * @Version 1.0 2017-9-12 下午7:30:10 王斌(wangb@unimlink.com) 创建
	 */
	private boolean checkAb1Quality(File outdir, File ab1File, List<Object> row) throws Exception {
		String refseq = outdir.getPath() + File.separatorChar + "ref.seq";
		String abi = ab1File.getPath();
		String command = "Rscript " + JzjyErpConfig.getContent("abi_command_path") + "abi.R -ref " + refseq + " -abi " + abi + " -od " + outdir.getPath() + "  -indel 0";
		logger.error(">>>>>>>>>>> Rscript :" + command);
		return runtimeExec(command);
	}
	
	/**
	 * Description:  执行截图
	 * @Version 1.0 2017-9-15 下午6:28:44 王斌(wangb@unimlink.com) 创建
	 * @param outdir 一代项目素材目录
	 */
	private boolean screenshot(File outdir) throws Exception {
		String basedir = JzjyErpConfig.getContent("abi_command_path");
		// 截图
		String command = "perl " + JzjyErpConfig.getContent("abi_command_path") + "sign_circle.pl -c " + JzjyErpConfig.getContent("abi_command_path") + "makebox " + outdir.getPath() + "/chromatogram.png ";
		logger.error(">>>>>>>>>>> perl :" + command);
		return runtimeExec(command);
	}
	
	/**
	 * Description: 获取TempBed 生成内容
	 * @Version 1.0 2017-9-12 下午7:09:58 王斌(wangb@unimlink.com) 创建
	 */
	private String getTempBedFileContent(String[] position) {
		String position_ = position[1].trim();
		if(position_.contains("-"))
			position_ = position_.split("-")[0];
			
		int seq_start = Integer.parseInt(position_) - 1000;
		int seq_end = 0;
		String chrm = position[0].replaceAll("chrx", "chrX").replaceAll("chrm", "chrM");
		if (chrm.equals("chrM")) {
			seq_end = ((Integer.parseInt(position_) + 1000 - 1) < 16569) ? (Integer.parseInt(position[1]) + 1000 - 1) : 16569;
		} else {
			seq_end = Integer.parseInt(position_) + 1000 - 1;
		}
		return chrm + "\t" + seq_start + "\t" + seq_end + "\n";
	}
	
	/**
	 * Description: 修改一代信息并执行流程
	 * @Version 1.0 2017-9-15 下午2:52:02 王斌(wangb@unimlink.com) 创建
	 * @param report 报告信息
	 * @param list   一代信息
	 * @param msg    失败消息
	 */
	private void modifyYidaisAndExecuProcess(ErpReport report, List<ErpYidai> ylist, String msg) throws Exception {
		// 修改一代信息,必然成功,如失败，保存失败信息msg
		for (ErpYidai entity : ylist) {
			entity.setStatus(YidaiStatus.ABIAnalysis.getValue());
			entity.setMsg(msg);
			entity.setUpdated(DateUtil.getSysDateTime());
			this.sequencingJudgeService.updateSequencingJudge(entity);
		}
		//是否存在不是测序项目失败，或不是ab1分析状态的，如都没有，将报告设为已完整
		Integer count_ = this.yidaiService.findNotInByYidaiStatus(report.getId(),Arrays.asList(new YidaiStatus[]{YidaiStatus.CexuProjectFail,YidaiStatus.ABIAnalysis}));
		if(count_ == 0){
			report.setYidaiComplete(YiDaiCompleted.Yes.getValue());
		}
		// 修改报告休息
		report.setYidaiMarked(YiDaiMarked.No.getValue());
		report.setStatus(ReportStatus.AbiFenxi.getValue());
		report.setUpdated(DateUtil.getSysDateTime());
		this.reportAuditService.updateReport(report);
		
		// 执行流程
		// 该报告是否在当前流程
		if (this.processService.checkCurrentProcessForFamilyReport(report.getReportId())) {
			boolean flag = this.executionService.completProcess(report.getReportId(), loginuser.getId());
			List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(report.getReportId());
			// 关联家系执行流程
			if (list != null && !list.isEmpty()) {
				for (ErpProject project : list) {
					// 家系是否在当前流程
					if (this.processService.checkCurrentProcessForFamilyReport(project.getProjectId())) {
						this.executionService.completProcess(project.getProjectId(), loginuser.getId());
					}
				}
			}
		}
	}
	
	/**
	 * Description: 验证一代分析与ab1文件是否匹配
	 * @Version 1.0 2017-9-14 下午8:04:58 王斌(wangb@unimlink.com) 创建
	 * @param analysisId   一代分析id
	 * @param abiDirectory abi文件解压文件夹
	 * @param excelList    一带数据list
	 */
	private Map<String, Integer> check(Integer analysisId, File abiDirectory, List<List<Object>> excelList) throws Exception {
		Map<String, String> names = this.getAb1FileNames(abiDirectory);
		if (names.isEmpty()) {
			return new HashMap<String, Integer>();
		}
		// 比较excel文件中的项目名和ab1文件中的项目名是否匹配
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (List<Object> row : excelList) {
			String yidaiId = StringHelper.convertStr(row.get(4));
			if (!names.containsKey(yidaiId)) {
				continue;
			}
			ErpYidaiAbimeta abi = new ErpYidaiAbimeta();
			abi.setAnalysisId(analysisId);
			abi.setSubjectName(StringHelper.convertStr(row.get(2)));
			abi.setFamilyId(StringHelper.convertStr(row.get(1)));
			abi.setIndividualName(StringHelper.convertStr(row.get(2)));
			abi.setIndividualId(StringHelper.convertStr(row.get(1)));
			abi.setProjectId(StringHelper.convertStr(row.get(4)));
			abi.setGene(StringHelper.convertStr(row.get(5)));
			abi.setTransctiptId(StringHelper.convertStr(row.get(5)));
			abi.setExonId(StringHelper.convertStr(row.get(7)));
			abi.setNucChange(StringHelper.convertStr(row.get(8)));
			abi.setAaChange(StringHelper.convertStr(row.get(9)));
			abi.setChrpos(StringHelper.convertStr(row.get(10)));
			abi.setDepth(StringHelper.convertStr(row.get(11)));
			abi.setHomHet(StringHelper.convertStr(row.get(12)));
			abi.setAbiPath(StringHelper.convertStr(row.get(13)));
			Integer abiId = yidaiAbimetaService.createYidaiAbimeta(abi);
			map.put(yidaiId, abiId);
		}
		return map;
	}
	
	/**
	 * Description: 获取ab1文件名的一代项目编号
	 * @Version 1.0 2017-9-14 下午7:47:04 王斌(wangb@unimlink.com) 创建
	 */
	private Map<String, String> getAb1FileNames(File abiDirectory) throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		File[] files = abiDirectory.listFiles();
		// S160215C0062A.SH16010258.0214-56F.ab1
		for (File file : files) {
			String ab1Name = file.getName();
			ab1Name = ab1Name.replace(".", "_");
			String name = ab1Name.split("_")[1];
			map.put(name, file.getPath());
		}
		return map;
	}
	
	/**
	 * Description: 生成相关文件
	 * @Version 1.0 2017-9-14 下午6:43:00 王斌(wangb@unimlink.com) 创建
	 */
	private Map<String, Object> generateFiles(ErpReport report,List<ErpYidai> yidaiList) throws Exception {
		Map<String, Object> map = new HashMap<String, Object>();
		
		Runtime.getRuntime().exec("chmod 777 " + analysisPath + "/" + calendar + "/");  
		File file = FileUtil.newFile(analysisPath + "/" + calendar + "/" + report.getReportId());
		if (!file.exists()) {
			file.mkdirs();
		}
		String abizip = file.getPath() + "/abi.zip";
		// 创建abi空压缩文件为压缩做准备
		File abiFile = FileUtil.newFile(abizip);
		abiFile.createNewFile();
		
		// 创建abi空文件夹，把报告下所有选点的ab1文件全部拷贝至此目录下以便压缩
		File abiDir =FileUtil.newFile(file.getPath() + "/abi/");
		if (!abiDir.isDirectory()) {
			abiDir.mkdirs();
		}
		
		String abixlsx = file.getPath() + "/abi.xlsx";
		// 生成一代abi.zip,abi.xls
		this.generateYidaisExcel(report,yidaiList, map, abixlsx, file.getPath(), abiDir.getPath());
		
		// 压缩ab1File文件
		CompressionZip.zip(abiFile.getPath(), abiDir);
		
		// 删除压缩完成的文件夹
		analysisService.deleteAllFilesOfDir(abiDir);
		
		map.put("abizip", abizip);
		map.put("abixlsx", abixlsx);
		return map;
	}
	
	/**
	 * Description: 生成一代abi.zip,abi.xls
	 * @Version 1.0 2017-9-14 下午6:38:11 王斌(wangb@unimlink.com) 创建
	 * @param yidaiList 一代信息
	 * @param xlsPath  xls路径
	 * @param filePath 分析路径
	 * @param abiDir   上传的ab1文件路径，以便压缩
	 */
	private void generateYidaisExcel(ErpReport report,List<ErpYidai> yidaiList, Map<String, Object> map, String xlsPath, String filePath, String abiDir) throws Exception {
		// 获取excel文件路径
		File xlsxFile = FileUtil.newFile(xlsPath);
		
		// 组建excel表
		XSSFWorkbook book = new XSSFWorkbook();
		XSSFSheet sheet = book.createSheet();
		XSSFRow row = sheet.createRow(0);
		row.createCell(0).setCellValue("患者姓名");
		row.createCell(1).setCellValue("家系编号");
		row.createCell(2).setCellValue("患者姓名");
		row.createCell(3).setCellValue("样本编号");
		row.createCell(4).setCellValue("项目编号");
		row.createCell(5).setCellValue("基因");
		row.createCell(6).setCellValue("转录本编号");
		row.createCell(7).setCellValue("Exon编号");
		row.createCell(8).setCellValue("核苷酸变化");
		row.createCell(9).setCellValue("氨基酸变化");
		row.createCell(10).setCellValue("染色体位置");
		row.createCell(11).setCellValue("测序深度");
		row.createCell(12).setCellValue("Hom/Het");
		row.createCell(13).setCellValue("ab1路径");
		row.createCell(14).setCellValue("outdir");
		row.createCell(15).setCellValue("abifile");
		int i = 1;
		for (ErpYidai yidai : yidaiList) {
			// 一代表中ab1文件全路径
			File ab1File = FileUtil.newFile(ab1Path + "/" + yidai.getAbipath());
			
			// 为abi文件夹添加该一代ab1空文件以准备拷贝
			String ab1Name = ab1File.getName();
			File abiPath = FileUtil.newFile(abiDir + "/" + ab1Name);
			
			// 创建一代文件包加ab1文件
			File yidaiDirectory = FileUtil.newFile(filePath + "/" + yidai.getYidaiId());
			if (!yidaiDirectory.exists()) {
				yidaiDirectory.mkdirs();
			}
			
			// 创建一代项目文件夹下ab1空文件以准备拷贝
			File yidaiFile = FileUtil.newFile(yidaiDirectory.getPath() + "/" + ab1Name);
			
			if (ab1File.exists() && ab1File.isFile()) {
				// 拷贝一代表中ab1文件至abi文件夹下
				boolean copyFile = FileUtil.copyFile(ab1File, abiPath);
				// 拷贝一代表中ab1文件至一代项目文件夹下
				boolean ydabiCopyFile = FileUtil.copyFile(ab1File, yidaiFile);
				if (!(copyFile && ydabiCopyFile)) {
					logger.error("YidaiFamilyReportAnalysis : copy ab1File to abiPath fail.  ab1File[" + ab1File.getPath() + "]-abiPath[" + abiPath.getPath() + "]-yidaiFile[" + yidaiFile.getPath() + "]");
					continue;
				}
			} else {
				logger.error("YidaiFamilyReportAnalysis : ab1File not is file.");
				continue;
			}
			//["SCN10A", "c.53C>G", "chr3-38835449", "p.P18R", "", ""]
			//["PAH","c.-50-u6657_-50-u6656insA","chr12:103288555","promoter","Hom","40(1)"]
			List<String> list = JSON.parseArray(yidai.getGene(), String.class);
//			String cHGVSOrCHGVS = list.get(1);
//			cHGVSOrCHGVS = cHGVSOrCHGVS.replace("\\", "/");
//			String[] cHGVS_or_cHGVS = cHGVSOrCHGVS.split("_");
			
			// 向填充excel数据
			row = sheet.createRow(i);
			row.createCell(0).setCellValue((StringUtils.isEmpty(yidai.getXianzhengzhe()) ? "-" : yidai.getXianzhengzhe()));
			row.createCell(1).setCellValue(report.getReportId());
			row.createCell(2).setCellValue((StringUtils.isEmpty(yidai.getName()) ? "-" : yidai.getName()));
			row.createCell(3).setCellValue(report.getSampleId());
			row.createCell(4).setCellValue(yidai.getYidaiId());
			row.createCell(5).setCellValue(list.get(0));
			row.createCell(6).setCellValue("-");
			row.createCell(7).setCellValue("-");
			row.createCell(8).setCellValue((StringUtils.isEmpty(list.get(1)) ? "-" : list.get(1)));
			row.createCell(9).setCellValue((list.size()<=3||StringUtils.isEmpty(list.get(3)) ? "-" : list.get(3)));
			
			String chr1 = list.get(2).trim();
			chr1 = chr1.replaceAll("：", ":").replaceAll(":", "-");
			String[] chr1arr = chr1.split("-");
			String position = "";
			if(chr1arr.length > 2){
				position = chr1arr[0] + ":" + chr1arr[1];
			}else{
				position = chr1.replaceAll("-", ":");
			}
			
			row.createCell(10).setCellValue(position);
			row.createCell(11).setCellValue((list.size()<=5||list.get(5) == null ? "-" : list.get(5)));
			row.createCell(12).setCellValue((list.size()<=4||list.get(4) == null ? "-" : list.get(4)));
			row.createCell(13).setCellValue(yidai.getAbipath());
			row.createCell(14).setCellValue(yidaiDirectory.getPath());
			row.createCell(15).setCellValue(yidaiFile.getPath());
			i++;
		}// 获取该报告下的所有一代信息循环的结尾
		
		// 保存excel
		book.write(new FileOutputStream(xlsxFile));
		book.close();
	}
	
	/**
	 * Description: 创建一代项目
	 * @Version 1.0 2017-9-14 下午1:59:47 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiProject createProject(ErpReport report) throws Exception {
		ErpYidaiProject entity = new ErpYidaiProject();
		entity.setTitle(report.getReportId());
		entity.setUserId(loginuser.getId());
		entity.setCreated(DateUtil.getSysDateTime());
		this.yidaiProjectService.createYidaiPorject(entity);
		return entity;
	}
	
	/**
	 * Description: 创建一代分析
	 * @Version 1.0 2017-9-14 下午2:14:29 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiAnalysis createAnalysis(ErpReport report,Integer projectId) throws Exception {
		ErpYidaiAnalysis entity = new ErpYidaiAnalysis();
		entity.setTitle(report.getReportId());
		entity.setType("abi");
		entity.setProjectId(projectId);
		entity.setUserId(loginuser.getId());
		entity.setCreated(DateUtil.getSysDateTime());
		this.yidaiAnalysisService.createYidaiAnalysis(entity);
		return entity;
	}
	
	/**
	 * Description: 创建一代相关文件表abi.zip数据
	 * @Version 1.0 2017-9-14 下午3:28:19 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiFile createYidaiZip(ErpReport report,Integer projectId, Integer analysisId) throws Exception {
		ErpYidaiFile entity = new ErpYidaiFile();
		entity.setUserId(loginuser.getId());
		entity.setProjectId(projectId);
		entity.setFilename("abi.zip");
		entity.setFilepath(calendar + "/" + report.getReportId() + "/abi.zip");
		Map<String, Object> zipMap = new HashMap<String, Object>();
		zipMap.put("analysis", analysisId);
		entity.setMeta(JsonUtil.ObjectToJson(zipMap));
		entity.setType("abimeta");
		entity.setCreated(DateUtil.getSysDateTime());
		yidaiFileService.createYidaiFile(entity);
		return entity;
	}
	
	/**
	 * Description: 创建一代相关文件表abi.xlsx数据
	 * @Version 1.0 2017-9-14 下午3:30:25 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiFile createYidaiExcel(ErpReport report,Integer projectId, Integer yidaiZipId) throws Exception {
		ErpYidaiFile entity = new ErpYidaiFile();
		entity.setUserId(loginuser.getId());
		entity.setProjectId(projectId);
		entity.setFilename("abi.xlsx");
		entity.setFilepath(calendar + "/" + report.getReportId() + "/abi.xlsx");
		Map<String, Object> xlsxMap = new HashMap<String, Object>();
		xlsxMap.put("abi", yidaiZipId);
		entity.setMeta(JsonUtil.ObjectToJson(xlsxMap));
		entity.setType("bed");
		entity.setCreated(DateUtil.getSysDateTime());
		this.yidaiFileService.createYidaiFile(entity);
		return entity;
	}
	
	/**
	 * Description: 创建prepare_abi_task表信息
	 * @Version 1.0 2017-9-14 下午3:39:32 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiTask createPrepareAbiTask(Integer projectId, Integer analysisId, Integer yidaiZipId, Integer yidaiXlsxId) throws Exception {
		ErpYidaiTask entity = new ErpYidaiTask();
		entity.setUserId(loginuser.getId());
		entity.setProjectId(projectId);
		entity.setAnalysisId(analysisId);
		entity.setType("prepare_abi");
		entity.setStatus("pending");
		List<Object> abifilePk = new ArrayList<Object>();
		abifilePk.add(yidaiZipId);
		abifilePk.add(yidaiXlsxId);
		
		Map<String, Object> parametersMap = new HashMap<String, Object>();
		parametersMap.put("inputs", abifilePk);
		entity.setParameters(JsonUtil.ObjectToJson(parametersMap));
		entity.setDelay(ErpYidaiTask.TaskDelay.No.getValue());
		entity.setCreated(DateUtil.getSysDateTime());
		this.yidaiTaskService.createYidaiTask(entity);
		return entity;
	}
	
	/**
	 * Description: 修改任务结果
	 * @Version 1.0 2017-9-15 下午3:42:46 王斌(wangb@unimlink.com) 创建
	 * @param entity     任务对象
	 * @param status     结果状态 Finished,failed
	 * @param prepareAbi 是否abi准备
	 * @param msg        消息内容
	 */
	private ErpYidaiTask updateTaskForResult(ErpReport report,ErpYidaiTask entity, ResultStatus status, boolean prepareAbi, String msg) {
		String succ = "";
		if (ResultStatus.Finished == status) {
			if (prepareAbi)
				succ = "{\"result\": [{\"inputs\": \"" + report.getReportId() + "\"}]}";
			else
				succ = "{\"result\": [{\"abi_metas\": " + msg + "}]}";
		}
		String json = "{\"status\": \"success\", \"errormsg\": \"" + msg + "\", \"successmsg\":" + succ + "}";
		// result组建
		entity.setResult(json);
		entity.setStatus((ResultStatus.Finished == status) ? "finished" : "failed");
		yidaiTaskService.updateYidaiTask(entity);
		return entity;
	}
	
	/**
	 * Description: 创建abi结果
	 * @Version 1.0 2017-9-15 下午3:12:43 王斌(wangb@unimlink.com) 创建
	 */
	private ErpYidaiAbiresult createAbiresult(ErpReport report,Integer analysisId) {
		ErpYidaiAbiresult entity = new ErpYidaiAbiresult();
		entity.setAnalysisId(analysisId);
		entity.setFamilyId(report.getReportId());
		this.yidaiAbiresultService.createYidaiAbiresult(entity);
		return entity;
	}
	
	/**  
	 * Description: 执行命令
	 * @Version 1.0 2017年8月23日 下午7:09:39 韩旭(hanxu@unimlink.com) 创建
	 */
	private boolean runtimeExec(String command) throws Exception {
		Runtime runtime = Runtime.getRuntime();
		String[] cmd = new String[]{"sh", "-c", command};
		Process process = runtime.exec(cmd);
		// 打印程序输出
		readProcessOutput(process);
		int flag = process.waitFor();
		if (flag == 0) {
			logger.error(">>>>>>>>>>>>>>>>>>" + command + " -- > 执行成功!<<<<<<<<<<<<<<<<<<<");
			return true;
		} else {
			logger.error(">>>>>>>>>>>>>>>>>>" + command + " -- > 执行失败!<<<<<<<<<<<<<<<<<<<");
			return false;
		}
	}
	
	/**
   * 打印进程输出
   *
   * @param process 进程
   * @throws Exception 
   */
	private static void readProcessOutput(final Process process) throws Exception {
    // 将进程的正常输出在 System.out 中打印，进程的错误输出在 System.err 中打印
    read(process.getInputStream(), System.out);
    read(process.getErrorStream(), System.err);
	}
	
	//读取输入流
	private static void read(InputStream inputStream, PrintStream out) throws Exception {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = reader.readLine()) != null) {
				out.println(">>>>>>>>>>>>>>>>>>>>>>>> readProcessOutput: " + line);
			}
		} finally {
			inputStream.close();
		}
	}
	
}