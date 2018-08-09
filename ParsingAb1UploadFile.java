package com.jzjy.erp.thread;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.jzjy.erp.Constants;
import com.jzjy.erp.config.JzjyErpConfig;
import com.jzjy.erp.entity.ErpPrimer;
import com.jzjy.erp.entity.ErpPrimer.Rukustatus;
import com.jzjy.erp.entity.ErpProject;
import com.jzjy.erp.entity.ErpReport;
import com.jzjy.erp.entity.ErpReport.ReportStatus;
import com.jzjy.erp.entity.ErpReport.YiDaiCompleted;
import com.jzjy.erp.entity.ErpReport.YiDaiMarked;
import com.jzjy.erp.entity.ErpUserbase;
import com.jzjy.erp.entity.ErpYidai;
import com.jzjy.erp.entity.ErpYidai.JudgeCondition;
import com.jzjy.erp.entity.ErpYidai.YidaiStatus;
import com.jzjy.erp.service.ExecutionService;
import com.jzjy.erp.service.PrimerLibrarService;
import com.jzjy.erp.service.ProcessService;
import com.jzjy.erp.service.ProjectService;
import com.jzjy.erp.service.ReportAuditService;
import com.jzjy.erp.service.ReportMakeService;
import com.jzjy.erp.service.SequencingJudgeService;
import com.jzjy.erp.service.YidaiService;
import com.jzjy.erp.utils.DateUtil;
import com.jzjy.erp.utils.FileUtil;
import com.jzjy.erp.utils.SessionUtil;

/**
 * Description: 异步解析ab1文件
 * @version 1.0 2017-9-12 下午4:55:22 王斌 (wangb@unimlink.com) created
 */
public class ParsingAb1UploadFile implements Runnable {
	
	Logger logger = Logger.getLogger(ParsingAb1UploadFile.class);
	
	YidaiService yidaiService = Constants.getService(YidaiService.class);
	SequencingJudgeService sequencingJudgeService = Constants.getService(SequencingJudgeService.class);
	ReportMakeService reportMakeService = Constants.getService(ReportMakeService.class);
	PrimerLibrarService primerLibrarService = Constants.getService(PrimerLibrarService.class);
	ReportAuditService reportAuditService = Constants.getService(ReportAuditService.class);
	ExecutionService executionService = Constants.getService(ExecutionService.class);
	ProjectService projectService = Constants.getService(ProjectService.class);
	ProcessService processService = Constants.getService(ProcessService.class);
	
	private String zipPath;
	private String date;
	private String name;
	private HttpSession session;
	
	public ParsingAb1UploadFile(String zipPath, String date, String name, HttpSession session) {
		super();
		this.zipPath = zipPath;
		this.date = date;
		this.name = name;
		this.session = session;
	}
	
	@Override
	public void run() {
		try {
			// 获取解压的文件夹下的所有文件
			String unzipdir = zipPath + File.separatorChar + date + File.separatorChar + name + File.separatorChar; // 解压后目录
			File abiRoot = FileUtil.newFile(unzipdir);
			if (!abiRoot.exists() || !abiRoot.isDirectory()) {
				logger.error("ParsingAb1UploadFile : unzipdir is null.");
				return;
			}
			File[] abiFiles = abiRoot.listFiles();
			if (abiFiles == null || abiFiles.length <= 0) {// 解压的文件夹下没有文件
				logger.error("ParsingAb1UploadFile : abiFiles is null.");
				return;
			}

			Map<Integer,Integer> reportMap = new LinkedHashMap<Integer,Integer>(); 
			// 遍历解压的文件夹
			for (File abiFile : abiFiles) {
				// 获取当前文件名称
				String ab1Name = abiFile.getName();
				// 截取当前文件名称
				ab1Name = ab1Name.replace(".", "_");
				logger.error(">>>>>>>>>>>> ab1Name:"+ ab1Name);
				String[] nameArr = ab1Name.split("_");
				// 判断文件后缀是不是ab1
				if(!ab1Name.endsWith("ab1")){
					logger.error("ParsingAb1UploadFile : filename not is ab1["+ab1Name+"].");
					abiFile.delete();
					continue;
				}

				ErpYidai yidai = this.sequencingJudgeService.findByYidaiId(nameArr[1]);
				if (yidai == null || StringUtils.isBlank(yidai.getGene())) {
					logger.error("ParsingAb1UploadFile : yidai is null.");
					abiFile.delete();
					continue;
				}
//				if(YidaiStatus.CexeStart.getValue() == yidai.getStatus()){
//					logger.error("ParsingAb1UploadFile : ["+yidai.getYidaiId()+"] ongoing ab1 analysis.");
//					abiFile.delete();
//					continue;
//				}
				//判断是否测序成功
				if(YidaiStatus.ManualRework.getValue() < yidai.getStatus()){
					logger.error("ParsingAb1UploadFile : ["+yidai.getYidaiId()+"] it has been sequenced success.");
					abiFile.delete();
					continue;
				}
				
				String relative = DateUtil.getSysDateYmd() + File.separatorChar + yidai.getYidaiId() + File.separatorChar +  abiFile.getName();
				yidai.setAbipath(relative);
				yidai.setUpdated(DateUtil.getSysDateTime());
				yidai.setStatus(YidaiStatus.CexeStart.getValue());
				int num = this.sequencingJudgeService.updateSequencingJudge(yidai);
				if (num <= 0) {
					this.failYidai(yidai, abiFile, "update status fail.");
					continue;
				}
				ErpPrimer primer = this.primerLibrarService.findById(yidai.getPrimerId());
				if(primer == null){
					abiFile.delete();
					this.failYidai(yidai, abiFile, "primer is null.");
					continue;
				}

				boolean flag = this.getSequence(yidai, abiFile);
				if(!flag){
					this.failYidai(yidai, abiFile, "getSequence fali.");
					continue;
				}
				
				flag = this.checkAb1Quality(yidai, abiFile);
				if(!flag){
					this.failYidai(yidai, abiFile, "checkAb1Quality fali.");
					continue;
				}
				
				// 命令执行成功，修改相关文件
				yidai.setStatus(YidaiStatus.CexuSuccess.getValue());
				yidai.setUpdated(DateUtil.getSysDateTime());
				num = this.sequencingJudgeService.updateSequencingJudge(yidai);
				if (num <= 0) {
					this.failYidai(yidai, abiFile, "update yidai info fail.");
					continue;
				}
				
				primer.setRukustatus(Rukustatus.YiChuKu.getValue());
				num = this.primerLibrarService.updatePrimer(primer);
				if (num <= 0) {
					this.failYidai(yidai, abiFile, "update primer info fail.");
					continue;
				}
//				abiFile.delete();
				
				//将报告所关联一代项目关联
//				if(reportMap.containsKey(yidai.getReportId())){
//					String value = reportMap.get(yidai.getReportId());
//					reportMap.put(StringHelper.convertStr(yidai.getReportId()), value + yidai.getId() + ",");
//				}else{
//					reportMap.put(StringHelper.convertStr(yidai.getReportId()), yidai.getId() + ",");
//				}
				reportMap.put(yidai.getReportId(), yidai.getReportId());
			}
			
			if(!reportMap.isEmpty()){
				for (Integer key : reportMap.keySet()) {
					//TODO 失败只将一代信息设置为失败，同一报告中只要存在有一条成功选点信息，就算是完整，是否完整不包含 失败选点 by wangbin 20171023
					Integer reportId = key;
//					List<Integer> ids = StringHelper.toInts(reportMap.get(key), StringHelper.SPLIT_PARAM_THREE);
					//存在测序成功信息，哪怕是一条， 且其他一代信息，不包含手动项目失败或
 					Integer count = this.yidaiService.findByYidaiStatus(reportId,JudgeCondition.Equal, YidaiStatus.CexuSuccess);
					//报告下一代信息，除测序项目失败、测序成功状态的剩下的数量
					Integer count_ = this.yidaiService.findNotInByYidaiStatus(reportId,Arrays.asList(new YidaiStatus[]{YidaiStatus.CexuProjectFail,YidaiStatus.CexuSuccess,YidaiStatus.ABIAnalysis}));
					logger.error(">>>>>>>>>> ParsingAb1UploadFile -- reportId : "+ reportId +" count:" + count + " count_:"+count_);
					if (count > 0 && count_ == 0) {
						ErpReport report = reportMakeService.findById(reportId);
						if(report == null){
							logger.error("ParsingAb1UploadFile : ["+reportId+"] report is null.");
							continue;
						}
						report.setYidaiComplete(YiDaiCompleted.Yes.getValue());
						if(report.getStatus() < ReportStatus.CexuSuccess.getValue()){
							report.setStatus(ReportStatus.CexuSuccess.getValue());
						}
						report.setYidaiMarked(YiDaiMarked.No.getValue());
						report.setUpdated(DateUtil.getSysDateTime());
						int num = reportMakeService.updateReport(report);
						if (num > 0) {
							this.toSuccess(report);
						}
					}
				}
			}
			
			// zip包下所有ab1文件已经解析，删除文件
			logger.error(" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> " + zipPath + File.separatorChar + date + File.separatorChar);
			FileUtil.delAllFilesContainCurrentDirectory(zipPath + File.separatorChar + date + File.separatorChar);
//			FileUtil.forceDelete(new File(zipPath + File.separatorChar + date + File.separatorChar));
			File file = new File(abiRoot.getPath() + ".zip");
			file.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			File file = new File(zipPath);
			if(file.exists()){
				for (File subFile : file.listFiles()) {
					subFile.delete();
				}
			}
		}
	}
	
	/**
	 * Description:检测ab1质量 
	 * @Version 1.0 2017-9-12 下午7:30:10 王斌(wangb@unimlink.com) 创建
	 */
	private boolean checkAb1Quality(ErpYidai entity,File abiFile) throws Exception {
		String ab1Path = JzjyErpConfig.getContent("sequencingJudge_save_ab1_path");
		String yidaiPath = ab1Path + File.separatorChar + DateUtil.getSysDateYmd() + File.separatorChar + entity.getYidaiId();
		String refseq = yidaiPath + File.separatorChar + "ref.seq";
		String abi = yidaiPath + File.separatorChar + abiFile.getName();
		
		String command = "Rscript " + JzjyErpConfig.getContent("abi_command_path") + "abi_quality.R -ref " + refseq + " -abi " + abi + " -od " + yidaiPath + "  -indel 0";
		logger.error(">>>>>>>>>>> Rscript :" + command);
		boolean flag = runtimeExec(command);
		if (flag) {
			return true;
		}
		return false;
	}
	
	
	/**
	 * Description: 获取序列
	 * @Version 1.0 2017-9-12 下午6:31:52 王斌(wangb@unimlink.com) 创建
	 */
	private boolean getSequence(ErpYidai entity,File abiFile) throws Exception{
		List<String> list = JSON.parseArray(entity.getGene(), String.class);
		if(list == null || list.isEmpty()){ return false;}
		//chr16:70977799-70977800
		//["LOC284600","n.-326T\u003eC","chr1:846488","promoter","Het","3(0.75)"]
		
		String[] position = list.get(2).split(":");
		String chrm = position[0].replaceAll("chrx", "chrX").replaceAll("chrm", "chrM");
		chrm = ("chrm".equalsIgnoreCase(chrm) ? "chrm.fa " : "hg19all.fa ");
		
		//验证下染色体位置
		try {
			String position_ = position[1].trim();
			position_ = position_.replaceAll("_", "-");
			if(position_.contains("-")){
				position_ = position_.split("-")[0];
				Integer.parseInt(position_);
			}else{
				Integer.parseInt(position[1]);
			}
		} catch (Exception e) {
			logger.error(">>>>>>>>>>>>>> getSequence Exception:" + e.getMessage());
			return false;
		}
		
		// 创建ab1文件保存目录
		String ab1Path = JzjyErpConfig.getContent("sequencingJudge_save_ab1_path");
		
		File yidaiPath = FileUtil.newFile(ab1Path + File.separatorChar + DateUtil.getSysDateYmd());
		yidaiPath = FileUtil.newFile(yidaiPath.getPath() + File.separatorChar + entity.getYidaiId());
		if (!yidaiPath.exists() || !yidaiPath.isDirectory()) {
			if (!yidaiPath.mkdirs()) {
				logger.error("ParsingAb1UploadFile : yidaiPath mkdirs fail.");
				return false;
			}
		}
		// 拷贝当前的ab1目录
		File copyAbiFile = FileUtil.newFile(yidaiPath.getPath() + File.separatorChar + abiFile.getName());
		if (!FileUtil.copyFile(abiFile, copyAbiFile)) {
			logger.error("ParsingAb1UploadFile : [" + abiFile.getName() + "]copy fail.");
			return false;
		}
		// 创建空文件
		File tempBedFile = FileUtil.newFile(yidaiPath.getPath() + File.separatorChar + "temp.bed");
		tempBedFile.createNewFile();
		File refSeqFile = FileUtil.newFile(yidaiPath.getPath() + File.separatorChar + "ref.seq");
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
		flag = runtimeExec(command);
		if (flag) {
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 失败一代
	 * @Version 1.0 2017-9-12 下午7:19:16 王斌(wangb@unimlink.com) 创建
	 */
	private int failYidai(ErpYidai entity,File abiFile,String errormsg) throws Exception{
		
		logger.error("ParsingAb1UploadFile : " + errormsg);
//		abiFile.delete();
		
		entity.setStatus(YidaiStatus.CexuFail.getValue());
		return this.sequencingJudgeService.updateSequencingJudge(entity);
	}
	
	/**
	 * Description: 获取TempBed 生成内容
	 * @Version 1.0 2017-9-12 下午7:09:58 王斌(wangb@unimlink.com) 创建
	 */
	private String getTempBedFileContent(String[] position) throws Exception {
		String position_ = position[1].trim();
		position_ = position_.replaceAll("_", "-");
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
	 * Description: 执行命令
	 * 
	 * @Version 1.0 2017年8月23日 下午7:09:39 韩旭(hanxu@unimlink.com) 创建
	 * @param command
	 * @return
	 * @throws Exception
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
	 * @param process 进程
	 * @throws Exception
	 */
	private static void readProcessOutput(final Process process) throws Exception {
		// 将进程的正常输出在 System.out 中打印，进程的错误输出在 System.err 中打印
		read(process.getInputStream(), System.out);
		read(process.getErrorStream(), System.err);
	}
	
	// 读取输入流
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
	
	/**
	 * Description: 下发执行流程
	 * @Version 1.0 2017年8月25日 下午7:20:27 韩旭(hanxu@unimlink.com) 创建
	 */
	private boolean toSuccess(ErpReport report) throws Exception {
		ErpUserbase loginuser = SessionUtil.getUserSession(session);
		//该报告是否在当前流程
		if(this.processService.checkCurrentProcessForCexuJudgment(report.getReportId())){
			boolean flag = this.executionService.completProcess(report.getReportId(), loginuser.getId());
			List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(report.getReportId());
			// 关联家系执行流程
			if (list != null && !list.isEmpty()) {
				for (ErpProject project : list) {
					//家系是否在当前流程
					if(this.processService.checkCurrentProcessForCexuJudgment(project.getProjectId())){
						this.executionService.completProcess(project.getProjectId(), loginuser.getId());
					}
				}
			}
			return flag;
		}
		return false;
	}
	
}
