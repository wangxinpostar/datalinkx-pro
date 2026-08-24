package com.datalinkx.dataserver.service.impl;

import com.datalinkx.common.constants.MetaConstants;
import com.datalinkx.common.exception.DatalinkXServerException;
import com.datalinkx.common.result.StatusCode;
import com.datalinkx.common.utils.Base64Utils;
import com.datalinkx.common.utils.ConnectIdUtils;
import com.datalinkx.common.utils.JsonUtils;
import com.datalinkx.dataserver.bean.domain.DsBean;
import com.datalinkx.dataserver.bean.domain.JobBean;
import com.datalinkx.dataserver.bean.vo.DsVo;
import com.datalinkx.dataserver.bean.vo.PageVo;
import com.datalinkx.dataserver.controller.form.DsForm;
import com.datalinkx.dataserver.repository.DsRepository;
import com.datalinkx.dataserver.repository.JobRepository;
import com.datalinkx.dataserver.service.DsService;
import com.datalinkx.dataserver.service.setupgenerator.*;
import com.datalinkx.driver.dsdriver.DsDriverFactory;
import com.datalinkx.driver.dsdriver.IDsDriver;
import com.datalinkx.driver.dsdriver.IDsReader;
import com.datalinkx.driver.dsdriver.base.model.DbTableField;
import com.datalinkx.security.utils.RedisCache;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.datalinkx.common.utils.IdUtils.genKey;
import static com.datalinkx.dataserver.monitor.DbConnectionMonitor.DB_CONNECTION_STATUS;

@Service
@Log4j2
public class DsServiceImpl implements DsService {

	@Autowired
	private DsRepository dsRepository;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private RedisCache redisCache;

	private static final Map<Integer, SetupInfoGenerator> SETUP_INFO_GENERATORS = new HashMap<>();

	@PostConstruct
	public void init() {
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.MYSQL, new MysqlSetupInfoGenerator());
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.ELASTICSEARCH, new EsSetupInfoGenerator());
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.ORACLE, new OracleSetupInfoGenerator());
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.REDIS, new RedisSetupInfoGenerator());
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.HTTP, new HttpSetupInfoGenerator());
		SETUP_INFO_GENERATORS.put(MetaConstants.DsType.KAFKA, new KafkaSetupInfoGenerator());
	}

	/**
	 * @Description //数据源创建
	 * @Date 2021/1/12 6:13 下午
	 * @param form
	 * @return String dsId
	 **/
	@Transactional(rollbackFor = Exception.class)
	@SneakyThrows
	public String create(DsForm.DsCreateForm form) {
		String dsId = genKey("ds");
		// 1、检测数据源是否重复，重名或者已接入
		DsBean nameCheck = dsRepository.findByName(form.getName());
		if (!ObjectUtils.isEmpty(nameCheck)) {
			throw new DatalinkXServerException(form.getName() + " 数据源名称存在");
		}

		// 2、构造数据源配置信息
		DsBean dsBean = new DsBean();
		dsBean.setDsId(dsId);
		dsBean.setType(form.getType());
		dsBean.setUsername(form.getUsername());
		dsBean.setHost(form.getHost());
		dsBean.setPort(form.getPort());
		dsBean.setName(form.getName());
		dsBean.setDatabase(form.getDatabase());
		dsBean.setConfig(form.getConfig());

		// 2.1、oracle类型数据源有schema概念
		if (MetaConstants.DsType.ORACLE.equals(form.getType())) {
			dsBean.setSchema(form.getDatabase());
		}

		if (form.getPassword() != null) {
			dsBean.setPassword(Base64Utils.encodeBase64(form.getPassword().getBytes(StandardCharsets.UTF_8)));
		}

		// 2.2、检查config字符串是否合法
		this.checkConfigFormat(dsBean);
		// 2.3、检查连接情况
		this.checkConnect(dsBean);

		dsRepository.save(dsBean);

		return dsId;
	}

	@Override
	public String test(String dsId) {
		redisCache.deleteObject(DB_CONNECTION_STATUS + dsId);
		DsBean dsBean = dsRepository.findByDsId(dsId)
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS, "数据源不存在"));
		// 2.2、检查config字符串是否合法
		this.checkConfigFormat(dsBean);
		// 2.3、检查连接情况
		try {
			this.checkConnect(dsBean);
			redisCache.setCacheObject(DB_CONNECTION_STATUS + dsId, "SUCCESS");
			return "SUCCESS";
		} catch (Exception e) {
			redisCache.setCacheObject(DB_CONNECTION_STATUS + dsId, "ERROR");
			throw new DatalinkXServerException(e);
		}
	}

	private void checkConfigFormat(DsBean dsBean) {
		if (!ObjectUtils.isEmpty(dsBean.getConfig())) {
			try {
				Map map = JsonUtils.toObject(dsBean.getConfig(), Map.class);

				// HTTP数据源校验url是否合法
				if (MetaConstants.DsType.HTTP.equals(dsBean.getType())) {
					String url = (String) map.get("url");
					if (ObjectUtils.isEmpty(url)) {
						throw new DatalinkXServerException(StatusCode.DS_CONFIG_ERROR, "接口地址未配置");
					}

					if (!url.contains("http") && !url.contains("https")) {
						throw new DatalinkXServerException(StatusCode.DS_CONFIG_ERROR, "接口地址格式不正确，请包含协议");
					}
				}
			} catch (Exception e) {
				log.error("dsbean config json format error", e);
				throw new DatalinkXServerException(StatusCode.DS_CONFIG_ERROR, "数据源附加信息转化Json格式异常");
			}
		}
	}

	public void checkConnect(DsBean dsBean) {
		try {
			IDsDriver ignored = DsDriverFactory.getDriver(getConnectId(dsBean));
			ignored.connect(true);
			log.info("connect success");
		} catch (Exception e) {
			log.error("connect error", e);
			throw new DatalinkXServerException(e.getMessage());
		}
	}

	public String getConnectId(DsBean dsBean) {
		SetupInfoGenerator generator = SETUP_INFO_GENERATORS.get(dsBean.getType());
		if (!ObjectUtils.isEmpty(generator)) {
			Object setupInfo = generator.generateSetupInfo(dsBean);
			return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(setupInfo));
		} else {
			Map<String, Object> map = new HashMap<>();
			map.put("type", MetaConstants.DsType.TYPE_TO_DB_NAME_MAP.get(dsBean.getType()));
			return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(map));
		}
	}

	public PageVo<List<DsVo>> dsPage(DsForm.DataSourcePageForm dataSourcePageForm) {

		PageRequest pageRequest = PageRequest.of(
				dataSourcePageForm.getPageNo() - 1,
				dataSourcePageForm.getPageSize());

		Page<DsBean> dsBeans = dsRepository.pageQuery(
				pageRequest,
				dataSourcePageForm.getName(),
				dataSourcePageForm.getType());

		PageVo<List<DsVo>> result = new PageVo<>();
		result.setPageNo(dataSourcePageForm.getPageNo());
		result.setPageSize(dataSourcePageForm.getPageSize());

		List<DsVo> dataList = new ArrayList<>();

		dsBeans.getContent().forEach(dsBean -> {

			// 密码存在时进行解密
			if (!ObjectUtils.isEmpty(dsBean.getPassword())) {
				try {
					String pwd = new String(
							Base64Utils.decodeBase64(dsBean.getPassword()),
							StandardCharsets.UTF_8);
					dsBean.setPassword(pwd);
				} catch (Exception e) {
					log.error("ds密码解析失败", e);
				}
			}

			// 无论有没有密码，都需要转换成 VO
			DsVo dsVo = new DsVo();
			BeanUtils.copyProperties(dsBean, dsVo);

			// 获取数据源连接状态
			String status = redisCache.getCacheObject(DB_CONNECTION_STATUS + dsBean.getDsId());

			dsVo.setStatus(status == null ? 0 : "SUCCESS".equals(status) ? 1 : 2);

			// 无论有没有密码，都加入返回结果
			dataList.add(dsVo);
		});

		result.setData(dataList);
		result.setTotalPage(dsBeans.getTotalPages());
		result.setTotal(dsBeans.getTotalElements());

		return result;
	}

	@Transactional(rollbackFor = Exception.class)
	public void del(String dsId) {
		// 校验任务依赖
		List<JobBean> dependJobs = jobRepository.findDependJobId(dsId);
		if (!ObjectUtils.isEmpty(dependJobs)) {
			throw new DatalinkXServerException(StatusCode.DS_HAS_JOB_DEPEND, "数据源存在流转任务依赖");
		}

		dsRepository.deleteByDsId(dsId);
	}

	public DsBean info(String dsId) {
		return dsRepository.findByDsId(dsId)
				.map(dsBean -> {
					if (!ObjectUtils.isEmpty(dsBean.getPassword())) {
						String pwd = null;
						try {
							pwd = new String(Base64Utils.decodeBase64(dsBean.getPassword()));
						} catch (UnsupportedEncodingException e) {
							log.error("ds密码解析失败");
						}
						dsBean.setPassword(pwd);
					}
					return dsBean;
				})
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS, "from ds not exist"));
	}

	public void modify(DsForm.DsCreateForm form) {
		Optional<DsBean> dsCheck = dsRepository.findByDsId(form.getDsId());
		DsBean dsBean = dsCheck
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS, "ds not exist"));
		if (MetaConstants.DsType.ORACLE.equals(form.getType())) {
			dsBean.setSchema(form.getDatabase());
		}
		dsBean.setUsername(form.getUsername());
		dsBean.setHost(form.getHost());
		dsBean.setPort(form.getPort());
		dsBean.setName(form.getName());
		dsBean.setDatabase(form.getDatabase());
		dsBean.setConfig(form.getConfig());
		dsRepository.save(dsBean);
	}

	@SneakyThrows
	public List<String> fetchTables(String dsId) {
		DsBean dsBean = dsRepository.findByDsId(dsId)
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
		List<String> tableList = new ArrayList<>();
		try {
			IDsDriver dsDriver = DsDriverFactory.getDriver(getConnectId(dsBean));
			if (dsDriver instanceof IDsReader) {
				IDsReader dsReader = (IDsReader) dsDriver;
				tableList = dsReader.treeTable(dsBean.getDatabase(), dsBean.getSchema());
			}
		} catch (Exception e) {
			log.error("connect error", e);
			throw new DatalinkXServerException(e);
		}
		return tableList;
	}

	public List<DsBean> list() {
		return Optional.ofNullable(dsRepository.findAllByIsDel(0))
				.orElse(Collections.emptyList()).stream()
				.sorted(Comparator.comparing(DsBean::getType)).collect(Collectors.toList());
	}

	public List<DbTableField> fetchFields(String dsId, String tbName) {
		DsBean dsBean = dsRepository.findByDsId(dsId)
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
		try {
			IDsDriver dsDriver = DsDriverFactory.getDriver(getConnectId(dsBean));
			if (dsDriver instanceof IDsReader) {
				IDsReader dsReader = (IDsReader) dsDriver;
				return dsReader.getFields(dsBean.getDatabase(), dsBean.getSchema(), tbName);
			}
		} catch (Exception e) {
			log.error("connect error", e);
			throw new DatalinkXServerException(e);
		}
		return new ArrayList<>();
	}

	@Override
	public List<Map<String, Object>> getTableData(String dsId, String tableName, Integer dataLength)
			throws UnsupportedEncodingException {
		DsBean dsBean = dsRepository.findByDsId(dsId)
				.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://" + dsBean.getHost() + ":" + dsBean.getPort() + "/" + dsBean.getDatabase());
		if (dsBean.getPassword() != null) {
			dsBean.setPassword(new String(Base64Utils.decodeBase64(dsBean.getPassword())));
		}
		dataSource.setUsername(dsBean.getUsername());
		dataSource.setPassword(dsBean.getPassword());
		List<Map<String, Object>> tableData = new ArrayList<>();
		try (Connection connection = dataSource.getConnection()) {
			String sql = "SELECT * FROM " + tableName + " LIMIT " + dataLength;
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(sql);

			// 获取列信息
			ResultSetMetaData metaData = resultSet.getMetaData();
			int columnCount = metaData.getColumnCount();

			// 将数据转换为适合图表的数据格式
			while (resultSet.next()) {
				Map<String, Object> row = new LinkedHashMap<>(); // 保留插入顺序
				for (int i = 1; i <= columnCount; i++) {
					row.put(metaData.getColumnName(i), resultSet.getObject(i));
				}
				tableData.add(row);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return tableData;
	}
}
