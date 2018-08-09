package com.jzjy.erp.action;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.seasar.struts.annotation.ActionForm;
import org.seasar.struts.annotation.Execute;
import org.seasar.struts.util.ResponseUtil;

import com.alibaba.fastjson.JSONArray;
import com.jzjy.erp.Code;
import com.jzjy.erp.TableCache;
import com.jzjy.erp.entity.ErpPrimer;
import com.jzjy.erp.entity.ErpPrimer.Origin;
import com.jzjy.erp.entity.ErpPrimer.PrimerStatus;
import com.jzjy.erp.entity.ErpPrimer.Rukustatus;
import com.jzjy.erp.entity.ErpProject;
import com.jzjy.erp.entity.ErpReport;
import com.jzjy.erp.entity.ErpReport.ReportStatus;
import com.jzjy.erp.entity.ErpSample.Heshuanruku;
import com.jzjy.erp.entity.ErpUserbase;
import com.jzjy.erp.entity.ErpYidai;
import com.jzjy.erp.entity.ErpYidai.YidaiStatus;
import com.jzjy.erp.entity.Primerdbprimers;
import com.jzjy.erp.form.YidaiForm;
import com.jzjy.erp.object.ResultObject;
import com.jzjy.erp.service.ExecutionService;
import com.jzjy.erp.service.PrimerLibrarService;
import com.jzjy.erp.service.PrimerdbprimersService;
import com.jzjy.erp.service.ProcessService;
import com.jzjy.erp.service.ProjectService;
import com.jzjy.erp.service.ReportAuditService;
import com.jzjy.erp.service.SampleService;
import com.jzjy.erp.service.VerifyDispatchingService;
import com.jzjy.erp.utils.DateUtil;
import com.jzjy.erp.utils.JsonUtil;
import com.jzjy.erp.utils.SessionUtil;
import com.jzjy.erp.utils.StringHelper;
import com.jzjy.erp.utils.TableFrameUtil;

/**
 * Description: 一代验证派工
 * @version 1.0  2017年8月2日 下午2:47:56 唐科 (tangke@unimlink.com) created
 */
@SuppressWarnings("rawtypes")
public class VerifyDispatchingAction extends ActionBase {
	
	Logger logger = Logger.getLogger(VerifyDispatchingAction.class);
	
	@ActionForm
	@Resource
	YidaiForm yidaiform;
	
	@Resource
	SampleService sampleService;
	@Resource
	ProjectService projectService;
	@Resource
	ReportAuditService reportAuditService;
	@Resource
	VerifyDispatchingService verifyDispatchingService;
	@Resource
	PrimerLibrarService primerLibrarService;
	@Resource
	PrimerdbprimersService primerdbprimersService;
	@Resource
	ProcessService processService;
	@Resource
	ExecutionService executionService;
	
	@Execute(validator = false)
	public String index() {
		return "index.jsp";
	}
	
	//引物重新设计,替换现有扩增位置
	@SuppressWarnings("unused")
	private void test1() throws Exception{
		String param = "'chr13:41381512-F','chr16:2153436-F-1','chr16:2153468-F-1','chr16:2153851-F-1','chr16:2165591-F-1','chr16:2159098-F-1','chr16:2156940-F-1','chr16:2153740-F-1','chr16:2167855-F-1','chr16:2160557-F-1','chr16:2160487-F-1','chr16:2166918-F-1','chr16:2167572-F-1','chr16:2163178-F-1','chr16:2147913-F-1','chr18:48604676-F-1','chr18:31323099-F-1','chr18:31323137-F-1','chr18:29104717-F-1','chr18:72997826-F-1','chr17:80042437-F-1','chr17:79479362-F-1','chr17:78343317-F-1','chr17:78343319-F-1','chr17:68172099-F-1','chr17:56357289-F-1','chr17:48703471-F-1','chr17:48247661-F-1','chr17:48247714-F-1','chr17:61568310-F-1','chr17:61555302-F-1','chr17:61566056-F-1','chr17:44143934-F-1','chr17:42331955-F-1','chr17:42991397-F-1','chr17:59861689-F-1','chr17:42462982-F-1','chr17:46020681-F-1','chr17:39727758-F-1','chr20:60899534-F-1','chr20:57480531-F-1','chr20:57485081-F-1','chr20:34025551-F-1','chr20:31023204-F-1','chr20:31016185-F-1','chr20:34713367-F-1','chr20:47990350-F-1','chr20:43961668-F-1','chr20:44048022-F-1','chr20:62038695-F-1','chr20:62038704-F-1','chr20:62324564-F-1','chr2:109382819-F-1','chrX:148069080-F-1','chrX:135077145-F-1','chrX:135741557-F-1','chrX:129148639-F-1','chrX:153593246-F-1','chrX:153608380-F-1','chrX:153593192-F-1','chrX:110928268-F-1','chrX:107845180-F-1','chrX:107826115-F-1','chrX:102192445-F-1','chrX:79932365-F-1','chrX:153171294-F-1','chrX:153171383-F-1','chr5:58481072-F-1','chr5:56177933-F-1','chr5:137206358-F-1','chr5:131329887-F-1','chr5:140070850-F-1','chr5:140062782-F-1','chr5:176637769-F-1','chr5:176562108-F-1','chr5:178416063-F-1','chr5:161322689-F-1','chr5:161322690-F-1','chr7:66103989-F-1','chr7:66103953-F-1','chr7:66460349-F-1','chr1:147380309-F-1','chr7:91603181-F-1','chr7:94040217-F-1','chr7:91830631-F-1','chr7:94028374-F-1','chr7:91865799-F-1','chr7:75211460-F-1','chr7:94053724-F-1','chr7:128619137-F-1','chr7:128480937-F-1','chr7:117188725-F-1','chr7:128477812-F-1','chr7:117375020-F-1','chr7:117176607-F-1','chr7:103275939-F-1','chr7:107315467-F-1','chr7:151945071-F-1','chr7:151927023-F-1','chr7:150704216-F-1','chr11:78369249-F-1','chr11:76867925-F-1','chr11:76922271-F-1','chr11:78369210-F-1','chr11:78419525-F-1','chr3:179122983-F-1','chr3:193333561-F-1','chr3:170727743-F-1','chr3:170723750-F-1','chr3:93593112-F-1','chr3:93595990-F-1','chr3:123366135-F-1','chr3:132175539-F-1','chr3:127327788-F-1','chr3:114070650-F-1','chr3:114070651-F-1','chr3:123366171-F-1','chr3:136012650-F-1','chr11:94225945-F-1','chr11:94192632-F-1','chr11:65487867-F-1','chr11:66481861-F-1','chr11:66455735-F-1','chr11:64572508-F-1','chr11:62402297-F-1','chr11:64517969-F-1','chr11:65487551-F-1','chr11:68452471-F-1','chr7:157202512-F-1','chr8:42742944-F-1','chr8:41801328-F-1','chr8:10467200-F-1','chr8:30585334-F-1','chr8:11566252-F-1','chrX:48546486-F-1','chrx:48650626-F-1','chr11:125483010-F-1','chr11:134129550-F-1','chr11:125478122-F-1','chr11:128782030-F-1','chr11:121477641-F-1','chr11:112101362-F-1','chr11:116662359-F-1','chr11:119044232-F-1','chr11:116661445-F-1','chr11:113295326-F-1','chr11:103327024-F-1','chr15:43892272-F-1','chr17:17699866-F-1','chr17:17701141-F-1','chr17:17707157-F-1','chr17:17696371-F-1','chr17:7906580-F-1','chr17:17701247-F-1','chr17:7915912-F-1','chr17:7578517-F-1','chr1:1960679-F-1','chr1:983695-F-1','chr1:1274813-F-1','chr6:30883000-F-1','chr6:30891190-F-1','chr6:44269113-F-1','chr6:42666061-F-1','chr6:34962160-F-1','chrX:8553320-F-1','chr1:201039491-F-1','chr1:181752920-F-1','chr1:201058520-F-1','chr1:169555513-F-1','chr1:158623179-F-1','chr1:155263001-F-1','chr1:152275298-F-1','chr1:154540564-F-1','chr1:154965222-F-1','chr1:154965223-F-1','chr1:154544341-F-1','chr1:152280559-F-1','chr2:152436091-F-1','chr20:3065244-F-1','chr20:1961096-F-1','chr20:2638879-F-1','chr3:58156437-F-1','chr3:58132651-F-1','chr22:19978295-F-1','chr22:19978296-F-1','chr22:19132037-F-1','chr2:179518430-F-1','chr19:13320168-F','chr19:2456763-F-1','chr7:66459197-F','chr4:6303071-F-1','chr4:25146734-F-1','chr4:6293740-F-1','chr2:1500418-F-1','chr2:1488479-F-1','chr2:1843083-F-1','chr16:31476100-F-1','chr16:30749181-F-1','chrX:18925161-F-1','chrX:32503110-F-1','chrX:32632464-F-1','chrX:30326777-F-1','chrx:22051132-F-1','chrx:22051133-F-1','chrX:18923933-F-1','chrX:32867910-F-1','chrX:32834604-F-1','chrX:15840943-F-1','chrx:32366527-F-1','chr11:1262104-F-1','chr11:47359005-F-1','chr11:46741296-F-1','chr11:47356671-F-1','chrX:53571987-F-1','chr16:86544837-F-1','chr16:83065748-F-1','chr16:81991565-F-1','chr16:50813764-F-1','chr16:50744822-F-1','chr16:67671603-F-1','chr16:74752984-F-1','chr16:74752951-F-1','chr16:47644808-F-1','chr3:49137655-F-1','chr3:49137395-F-1','chr7:44189371-F-1','chr7:6029571-F-1','chr7:45109531-F-1','chr7:40132628-F-1','chr7:2984024-F-1','chr7:286468-F-1','chr3:38647609-F-1','chr3:15685832-F-1','chr3:15507892-F-1','chr3:43122812-F-1','chr3:38592108-F-1','chr3:47125494-F-1','chr3:15495406-F-1','chr3:47162639-F-1','chr3:43121590-F-1','chr3:12650365-F-1','chr3:9724880-F-1','chr10:12111126-F-1','chr10:298382-F-1','chr3:71015203-F-1','chr16:1270194-F-1','chr16:1556960-F-1','chr16:1547440-F-1','chr16:3820813-F-1','chr16:1252234-F-1','chr16:2152905-F-1','chr16:2550288-F-1','chr1:7723923-F-1','chr1:10381786-F-1','chr1:6528135-F-1','chr19:8619563-F-1','chr16:89350089-F-1','chr16:89261345-F-1','chr16:90001643-F-1','chr16:9857974-F-1','chr16:15818116-F-1','chr16:11650555-F-1','chr16:14576606-F-1','chr16:16248508-F-1','chr12:5154539-F-1','chr12:974498-F-1','chr12:7050698-F-1','chr19:15291513-F-1','chr19:13318150-F-1','chr19:13050001-F-1','chr19:15285081-F-1','chr2:15694188-F-1','chr2:15519903-F-1','chr12:9231896-F-1','chr12:8804268-F-1','chr12:22040812-F-1','chr12:32893013-F-1','chr1:237850803-F-1','chr1:237951370-F-1','chr1:206623810-F-1','chr1:226109569-F-1','chr1:226109269-F-1','chr1:227170401-F-1','chr1:228547947-F-1','chr15:43906393-F-1','chr2:27726431-F-1','chr2:25965560-F-1','chr2:21255236-F-1','chr2:27720117-F-1','chr2:71825861-F-1','chr2:71185529-F-1','chr2:73799830-F-1','chr2:73678064-F-1','chr2:60679779-F-1','chr2:86067346-F-1','chr2:50850732-F-1','chr2:73680069-F-1','chr15:23892721-F-1','chr15:26793018-F-1','chr10:18439825-F-1','chr10:27294536-F-1','chr15:48773971-F-1','chr15:48766512-F-1','chr15:58253438-F-1','chr15:62277078-F-1','chr15:58834793-F-1','chr15:80460652-F-1','chr6:152698050-F-1','chr6:152751757-F-1','chr6:121768280-F-1','chr6:129609204-F-1','chr6:152540183-F-1','chr6:157150439-F-1','chr6:132181591-F-1','chr6:112469426-F-1','chr6:110064319-F-1','chr6:143094398-F-1','chr6:117687373-F-1','chr6:108243042-F-1','chr1:20976964-F-1','chr1:22446794-F-1','chr1:26140641-F-1','chr1:27023993-F-1','chr1:24671421-F-1','chr1:21904121-F-1','chr1:21904125-F-1','chr1:21904127-F-1','chr12:52159477-F-1','chr12:52309018-F-1','chr12:57965854-F-1','chr22:24720252-F-1','chr22:45683310-F-1','chr22:24560482-F-1','chr22:30074229-F-1','chr22:31333951-F-1','chr22:32179900-F-1','chr6:161990400-F-1','chr12:112926908-F-1','chr12:122277920-F-1','chr12:121437145-F-1','chr22:50512646-F-1','chr22:50518816-F-1','chr1:100684201-F-1','chr1:100715340-F-1','chr1:78401657-F-1','chr1:47882475-F-1','chr1:47882251-F-1','chr14:102431254-F-1','chr14:105180830-F-1','chr14:57271074-F-1','chr14:51095112-F-1','chr14:73637689-F-1','chr14:68272194-F-1','chr14:64574326-F-1','chr14:21161868-F-1','chr14:23882068-F-1','chr14:23862718-F-1','chr14:23889085-F-1','chr14:30132954-F-1','chr14:23858216-F-1','chr14:36217849-F-1','chr14:23885478-F-1','chr14:23885314-F-1','chr19:39008175-F-1','chr19:39037165-F-1','chr19:39068833-F-1','chr19:38951020-F-1','chr19:49671994-F-1','chr19:45998183-F-1','chr19:45412244-F-1','chr19:46268938-F-1','chr19:50728887-F-1','chr19:50781391-F-1','chr19:42364858-F-1','chr19:40900441-F-1','chr19:55447768-F-1','chr1:107963711-F-1','chr1:120478074-F-1','chr1:120295249-F-1','chr1:120307134-F-1','chr15:90627542-F-1','chr13:52515349-F-1','chr13:35733551-F-1','chr13:36225989-F-1','chr13:40256367-F-1','chr2:106002912-F-1','chr13:110818605-F-1','chr6:80223015-F-1','chr6:75827179-F-1','chr6:88228436-F-1','chr6:75899292-F-1','chr6:80197493-F-1','chr2:145157589-F-1','chr2:120020631-F-1','chr2:149226569-F-1','chr2:166848630-F-1','chr2:166011006-F-1','chr2:152432747-F-1','chr2:166854666-F-1','chr2:163123718-F-1','chr2:152423951-F-1','chr2:162274204-F-1','chr2:166187953-F-1','chr2:163128736-F-1','chr2:169833194-F-1','chr2:149793861-F-1','chr2:197184280-F-1','chr2:219677337-F-1','chr2:189872269-F-1','chr2:178969170-F-1','chr2:179529631-F-1','chr2:179611814-F-1','chr2:208988973-F-1','chr2:179604360-F-1','chr2:198353065-F-1','chr2:198361929-F-1','chr2:208992981-F-1','chr2:179419754-F-1','chr2:238249122-F-1','chr2:238249128-F-1','chr2:238287471-F-1','chr2:238267210-F-1','chr8:61750677-F-1','chr4:155508761-F-1','chr4:166999061-F-1','chr4:106155794-F-1','chr4:155505438-F-1','chr4:186068010-F-1','chr4:126238144-F-1','chr8:126036879-F-1','chr8:133192493-F-1','chr8:118830736-F-1','chr8:117862901-F-1','chr8:133141957-F-1','chr8:145740402-F-1','chr8:145641591-F-1','chr8:145640216-F-1','chr21:27484378-F-1','chr21:38884830-F-1','chr21:33039586-F-1','chr21:47404280-F-1','chr21:47531490-F-1','chr21:47419607-F-1','chr10:118969607-F-1','chr10:67680209-F-1','chr10:90701066-F-1','chr10:99508054-F-1','chr10:79396551-F-1','chr10:68280383-F-1','chr10:89720650-F-1','chr10:95791380-F-1','chr10:81318675-F-1','chr9:79867151-F-1','chr9:79867248-F-1','chr9:95482976-F-1','chr9:108363435-F-1','chr9:130588047-F-1','chr9:135944122-F-1','chr9:136305465-F-1','chr9:135163736-F-1','chr9:137688745-F-1','chr9:138664704-F-1','chr9:138676679-F-1','chr9:140967985-F-1','chr9:140052953-F-1','chr9:140711901-F-1','chr9:140052978-F-1','chr9:140695437-F-1','chr3:700008431-F-1','chr19:42474678-F-1','chr12:52156434-F-1','chrM:961-F-1-295','chrM:1494-F-246','chrM:1555-F','chry:2655608-F-247','chr17:4923912-F-1','chr4:9784332-F','chr20:10639284-F','chr19:15271796-F','chr11:17633781-F','chr10:18828309-F-1','chr14:23851252-F-2','chr22:24108345-F-1','chrx: 31496480-F-1','chr22:32188733-F-5-228R','chrX:32235179-F','chrX:38145491-F-2','chrX:38268005-F-1','chr7:42079730-F-1','chr15:42702630-F','chr19:44012186-F-201','chr20:44048986-F-1','chr20:44054324-F-1','chr6:49425427-F','chr12:64860755-F-1','chr17:78086416-F','chr17:78313347-F-1','chr17:78318978-F-1','chr10:79758791-F-456','chr10:79769439-F-1','chr14:91781993-F-1','chr791844054-F-1','chr8:96047755-F-1','chr8:96047755-F-1','chrX:99662726-F','chr12:103238134-F','chr12:103352123-F','chr13:111160305-F-1','chr11:118895980-F','chr9:126134464-F1','ch11:134127057-F-300','ch11:134131062-F-239','chr9:135947064-F','chr9:136218829-F','chr9:136220653-F','chr9:138649216-F-1','chr5:139494456-F-1','chr5:148392208-F','chr5:148411112-F-1','chrX:154159197-F','chr1:155207224-F','chr1:158587833-F-1','chr5:172410964-F-1','chr17:7489394-F-1','chrX:70368727-F-1','chrX:73751177-F-1','chrX:38260675-F-1','chrX:18959762-F-1','chrX:33229422-F-1','chr15:44859634-F-1','chr10:12162252-F-1','chrX:70444219-F-1','chrX:153760483-F-1','chrX:154134814-F-1','chr19:38983277-F-1','chrX:70443981-F-1','chrX:100653384-F-1','chrX:100662901-F-1','chr10:96725535-F-1','chr7:76019683-F-1','chr5:43656115-F-1','chrX:32407757-F-1','chr16:9927997-F-1','chrM:1494-F-1','chrM:961-F-1','chr5:37059153-F-1','chrX:47433759-F-1','chr19:50779261-F-1','chrX:153002668-F-1','chr9:131094450-F-1','chr4:159611504-F-1','chrX:32466612-F-1','chrX:19369519-F-1','chrX:32328217-F-1','chrX:148584998-F-1','chrX:133607492-F-1','chrX:31497121-F-1','chrX:153006173-F-1','chrX:32429928-F-1','chrX:70443828-F-1','chrX:48762056-F-1','chrX:30326727-F-1','chr14:102449926-F-1','chrX:31496480-F-1','chr6:161771208-F-1','chrX:32486674-F-1','chrX:100652878-F-1','chrX:7243380-F-1','chrX:32867853-F-1','chr19:55652315-F-1','chrx:38226585-F-1','chrx:152990969-F-1','chrX:31496441-F-1','chr7:30668220-F-1','chrX:71708892-F-1','chrX:70443670-F-1','chrX:108921236-F-1','chrX:41057815-F-1','chr10:78711850-F-1','chrX:153296078-F-1','chrX:153171204-F-1','chrX:67430060-F-1','chr10:78711911-F-1','chrx:30327326-F-1','chrX:128722876-F-1','chrX:148048444-F-1','chrX:148584995-F-1','chrX:153220047-F-1','chr19:11210992-F-1','chr9:131094462-F-1','chr16:2105487-F-1','chrx:101095845-F-1','chrX:18622949-F-1','chr10:71048518-F-1','chrX:153296684-F-1','chrX:153296272-F-1','chrX:153296265-F-1','chrX:153296256-F-1','chrX:153296262-F-1','chrX:153296269-F-1','chrX:153296273-F-1','chrX:122778633-F-1','chrX:53436069-F-1','chrX:135956571-F-1','chrM:12706-F-1','chr14:102481662-F-1','chrX:100658834-F-1','chr18:7033050-F-1','chrx:119576517-F-1','chr17:78349626-F-1','chr16:75663338-F-1','chrX:49084525-F-1','chrX:37652949-F-1','chrX:18943866-F-1','chrX:44935985-F-1','chrX:53239674-F-1','chrX:77243990-F-1','chrX:53658518-F-1','chrX:49084773-F-1','chr19:13387926-F-1','chrX:153001847-F-1','chrX:48370852-F-1','chrX:107930712-F-1','chrx:19372633-F-1','chrX:62898221-F-1','chrx:69718368-F-1','chrX:153589857-F-1','chrX:19377755-F-1','chrX:31241163-F-1','chr2:216242984-F-1','chrX:47434573-F-1','chrX:100653900-F-1','chrx:153774276-F-1','chrX:2853006-F-1','chrx:32398791-F-1','chr2:96950322-F-1','chrX:107866056-F-1','chrX:57407430-F-1','chrX:70444093-F-1','chrX:110963326-F-1','chrX:106884159-F-1','chrX:153764223-F-1','chrX:82763625-F-1','chr3:4699833-F-1','chrx:100656656-F-1','chrX:56591369-F-1','chr8:42297115-F-1','chr4:39255612-F-1','chr1:11317212-F-1','chr7:91622241-F-1'";
		List<Primerdbprimers> list = primerdbprimersService.findPrimerdbprimersByPositions(param);
		int index = 0;
		for (Primerdbprimers entity : list) {
			logger.error(">>>>>>>>>>>>>>>>>>>>>> Primerdbprimers : index :" + index + " entity.getPosition():" +entity.getPosition() );
			Map<String,String> map = this.primerLibrarService.primerDesign(200,entity.getPosition().trim());
			if(map == null){
				logger.error(">>>>>>>>>>>>>>>>>>>>>> primerDesign is null : index :" + index + " entity.getPosition():" +entity.getPosition() );
				continue;
			}
			entity.setPosition(StringUtils.deleteWhitespace(entity.getPosition().trim()));
			entity.setTargetLength(StringHelper.convertStr(map.get("targetLength")));
			String position = StringHelper.convertStr(map.get("targetPosition"));
			if(position.contains("-")){
				String[] targetPosition = position.split("-");
				entity.setTargetStart(targetPosition[0]);
				entity.setTargetEnd(targetPosition[1]);
			}else{
//				primer.setTargetStart(position);
//				primer.setTargetEnd(position);
			}
			entity.setTargetPosition(position);
			this.primerdbprimersService.updatePrimerdb(entity);
			index ++;
		}
		
		int num = 0;
		List<ErpPrimer> plist = primerLibrarService.findPrimerByPositions(param);
		for (ErpPrimer entity : plist) {
			logger.error(">>>>>>>>>>>>>>>>>>>>>> Primerdbprimers : index :" + index + " entity.getPosition():" +entity.getPosition() );
			Map<String,String> map = this.primerLibrarService.primerDesign(200,entity.getPosition().trim());
			if(map == null){
				logger.error(">>>>>>>>>>>>>>>>>>>>>> primerDesign is null : index :" + index + " entity.getPosition():" +entity.getPosition() );
				continue;
			}
			entity.setPosition(StringUtils.deleteWhitespace(entity.getPosition().trim()));
			entity.setTargetLength(StringHelper.convertStr(map.get("targetLength")));
			String position = StringHelper.convertStr(map.get("targetPosition"));
			if(position.contains("-")){
				String[] targetPosition = position.split("-");
				entity.setTargetStart(targetPosition[0]);
				entity.setTargetEnd(targetPosition[1]);
			}else{
//				primer.setTargetStart(position);
//				primer.setTargetEnd(position);
			}
			entity.setTargetPosition(position);
			this.primerLibrarService.updatePrimer(entity);
			num ++;
			logger.error(">>>>>>>>>>>>>>>>>>>>>> ErpPrimer : num :" + num);
		}
		
		
	}
	
	/**
	 * Description: 列表展示
	 * @Version 1.0 2017年8月2日 下午4:09:46 唐科(tangke@unimlink.com) 创建
	 */
	@Execute(validator = false)
	public String verifyDispatchingList() {
		try {
//			test1();
			TableCache cache = TableFrameUtil.gCache(session);
			if (cache != null && yidaiform.getPage() > 1) {
				cache.setPageId(yidaiform.getPage());
				cache.setPageSize(yidaiform.getPage_size());
				TableFrameUtil.out(cache);
				return null;
			}
			List<Map> list = this.verifyDispatchingService.findYidaiList(yidaiform);
			if (list != null && !list.isEmpty()) {
				TableCache tableCache = new TableCache();
				tableCache.setList(list);
				tableCache.setPageId(yidaiform.getPage());
				tableCache.setPageSize(yidaiform.getPage_size());
				TableFrameUtil.cCache(session, tableCache);
				TableFrameUtil.out(tableCache);
			} else {
				TableFrameUtil.cCache(session, new TableCache());
				ResultObject resultObject = new ResultObject();
				resultObject.setCode(Code.CODE_1);
				resultObject.setRows(new ArrayList());
				ResponseUtil.write(JsonUtil.ObjectToJson(resultObject), "utf-8");
			}
			return null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outError("列表异常。");
	}
	
	/**
	 * Description: 一代验证下发
	 * @Version 1.0 2017年8月3日 下午2:49:32 唐科(tangke@unimlink.com) 创建
	 */
	@Execute(validator = false)
	public String issued() throws Exception {
		
		String ids = request.getParameter("ids");
		if(StringUtils.isBlank(ids)){
			logger.error("issued : ids is null.");
			return outError("下发未成功。");
		}
		List<ErpYidai> list = this.verifyDispatchingService.findYidaiByIssued(ids);
		if(list == null || list.isEmpty()){
			logger.error("issued : yidai list is null.");
			return outError("下发未成功。");
		}
		
		try {
			Map<Integer,Map<String,List<ErpYidai>>> map = new HashMap<Integer, Map<String,List<ErpYidai>>>();
			for (ErpYidai entity : list) {
				//保存报告id
				if(StringUtils.isBlank(entity.getGene()) || "[]".equals(entity.getGene())){
					continue;
				}
				Map<String,List<ErpYidai>> primerMap = null;
				if(map.containsKey(entity.getReportId())){
					primerMap = map.get(entity.getReportId());
				}else{
					primerMap = new HashMap<String, List<ErpYidai>>(); 
				}
				//设置引物map
				this.setPrimerMap(primerMap, entity);
				map.put(entity.getReportId(), primerMap);
			}
			
			//验证报告下家系是否都已到达一代验证派工
			if(!map.isEmpty()){
				String names = "";
				Map<Integer,Map<String,List<ErpYidai>>> reports = new HashMap<Integer, Map<String,List<ErpYidai>>>();
				for (Integer reportId : map.keySet()) {
					ErpReport report = this.reportAuditService.findById(reportId);
					if(report == null) continue;
					String name = this.projectService.findFamilyProjectByProjectIdForName(report.getReportId(),true);
					if(StringUtils.isBlank(name)){
						//记录下可下发的一代
						reports.put(report.getId(), map.get(report.getId()));
					}else{
						names += StringHelper.convertStr(name) + ",";
					}
				}
				if(StringUtils.isNotBlank(names)){
					//将可以下发的，操作下发
					if(!reports.isEmpty()){
						this.issued(reports);
					}
					logger.error("issued : there is no nucleic acid in the family.");
					if(names.endsWith(",")) names = names.substring(0,names.length() - 1);
					
					return outMsg(Code.CODE_1008,names);
				}
			}
			boolean flag = this.issued(map);
			if(flag)
				return outMsg(Code.CODE_1, "下发成功。");
			return outError("下发失败。");
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e.getCause());
		}
	}
	
	/**
	 * Description: 下发
	 * @Version 1.0 2017-12-11 上午10:50:46 王斌(wangb@unimlink.com) 创建
	 */
	private boolean issued(Map<Integer,Map<String,List<ErpYidai>>> map) throws Exception{
		int num = 0;
		if(map != null && !map.isEmpty()){
			for (Map<String,List<ErpYidai>> msps : map.values()) {
				for (String key : msps.keySet()) {
					List<ErpYidai> yidais = msps.get(key);
					//创建引物并关联一代
					boolean flag = this.addPrimer(key,yidais);
					if(flag) { num ++;}
				}
			}
			
			if (num > 0) {
				this.executionProcess(map.keySet());
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Description: 设置引物map
	 * @Version 1.0 2017-9-26 下午2:50:30 王斌(wangb@unimlink.com) 创建
	 */
	private void setPrimerMap(Map<String, List<ErpYidai>> primerMap, ErpYidai entity) throws Exception {
		String chr1 = JSONArray.parseArray(entity.getGene()).getString(2);
		String gene = JSONArray.parseArray(entity.getGene()).getString(0);
		String key = chr1 + "," + gene + "," + entity.getReportId();
		// 染色体已存在，添加
		if (primerMap.containsKey(key)) {
			List<ErpYidai> list = primerMap.get(key);
			if (!list.isEmpty())
				list.add(entity);
		} else {
			List<ErpYidai> list = new ArrayList<ErpYidai>();
			list.add(entity);
			primerMap.put(key, list);
		}
	}
	
	/**
	 * Description: 创建引物
	 * @Version 1.0 2017-8-23 上午9:37:45 王斌(wangb@unimlink.com) 创建
	 */
	private synchronized boolean addPrimer(String key, List<ErpYidai> yidais) throws Exception {
		// 取出染色体位置
		String[] ranseti = key.split(",");
		// logger.error(">>>>>>>>>>>>>>>>>>>>>>>>> ranseti :" + ranseti);
		String position = ranseti[0];
		String gene = ranseti[1];
		position = position.replaceAll("：", ":").replaceAll("-", ":").replaceAll("_",":");
		String [] positionarr = position.split(":");
		if(positionarr.length > 2)
			position = positionarr[0] + ":" + positionarr[1];
		
		ErpPrimer erps = new ErpPrimer();
		// 拿到染色体位置去引物库中获取已验证过，且成功的引物
		Primerdbprimers primer = this.primerdbprimersService.findByPositionNormal(position.split(":")[0],position.split(":")[1]);
		int num = 0;
		if (primer != null) {// 有染色体，入库状态为：已入库
			ErpPrimer entity = this.primerLibrarService.findByPosition(primer.getPosition(),primer.getFprimerName(),primer.getRprimerName());
			if(entity == null){
				erps.setPosition(primer.getPosition());
				erps.setFprimerName(primer.getFprimerName());
				erps.setFprimer(primer.getFprimer());
				erps.setRprimerName(primer.getRprimerName());
				erps.setRprimer(primer.getRprimer());
				erps.setTargetLength(primer.getTargetLength());
				erps.setTargetPosition(primer.getTargetPosition());
				if(primer.getTargetPosition().contains("-")){
					String[] targetPosition = primer.getTargetPosition().split("-");
					erps.setTargetStart(targetPosition[0]);
					erps.setTargetEnd(targetPosition[1]);
				}
			}else{
				erps = entity;
			}
			erps.setGene(gene);
			erps.setCreated(DateUtil.parseDateToString(new Date()));
			erps.setOrigin(Origin.InLibrary.getValue());
			erps.setStatus(PrimerStatus.Indatabase.getValue());
			erps.setRukustatus(Rukustatus.Rukued.getValue());
			if(erps.getId() == null)
				num = this.primerLibrarService.addPrimer(erps);
			else
				num = this.primerLibrarService.updatePrimer(erps);
		} else {// 没有染色体 入库状态为待入库
			ErpPrimer entity = this.primerLibrarService.findByPosition(position);
			if(entity != null){
				erps = entity;
			}
			erps.setGene(gene);
			erps.setCreated(DateUtil.parseDateToString(new Date()));
			erps.setPosition(position);
			erps.setOrigin(Origin.NewDesign.getValue());
			erps.setStatus(PrimerStatus.Started.getValue());
			erps.setRukustatus(Rukustatus.DaiRuku.getValue());
			
			if(erps.getId() == null)
				num = this.primerLibrarService.addPrimer(erps);
			else
				num = this.primerLibrarService.updatePrimer(erps);
		}
		if (num > 0) {
			// 将一代关联引物
			for (ErpYidai entity : yidais) {
				entity.setPrimerId(erps.getId());
				entity.setStatus(YidaiStatus.PrimerDesign.getValue());
				entity.setUpdated(DateUtil.parseDateToString(new Date()));
				this.verifyDispatchingService.updateYidai(entity);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Description: 执行流程
	 * @Version 1.0 2017-8-23 上午10:32:32 王斌(wangb@unimlink.com) 创建
	 */
	private void executionProcess(Set<Integer> reports) throws Exception {
		ErpUserbase loginuser = SessionUtil.getUserSession(session);
		for (Integer reportId : reports) {
			ErpReport report = this.reportAuditService.findById(reportId);
			if (report == null) continue;
			
			// TODO 只要现在流程是在一代验证派工的，就走流程 by wangbin 20170919
			if (this.processService.checkCurrentProcessForVerifyDispatching(report.getReportId())) {
				report.setStatus(ReportStatus.PrimerDesign.getValue());
				report.setTimeStamp(reportAuditService.setTimestamp(report.getTimeStamp(), ReportStatus.YiDaiJX));
				report.setUpdated(DateUtil.parseDateToString(new Date()));
				int num = this.reportAuditService.updateReport(report);
				if (num <= 0) continue;
				
				// 核酸自动出库
				this.sampleService.hesuanChuku(report.getSampleId(), Heshuanruku.AlreadyOutBoundDepot);
				
				this.executionService.completProcess(report.getReportId(), loginuser.getId());
				List<ErpProject> list = this.projectService.findFamilyProjectByProjectId(report.getReportId());
				if (list != null && !list.isEmpty()) {
					for (ErpProject project : list) {
						//家系核酸自动出库
						this.sampleService.hesuanChuku(project.getSampleId(), Heshuanruku.AlreadyOutBoundDepot);
						// 家系是否在当前流程
						if (this.processService.checkCurrentProcessForVerifyDispatching(project.getProjectId()))
							this.executionService.completProcess(project.getProjectId(), loginuser.getId());
					}
				}
			}
		}
	}
}
