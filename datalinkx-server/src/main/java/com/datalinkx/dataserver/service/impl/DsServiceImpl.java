package com.datalinkx.dataserver.service.impl;

import cn.hutool.core.lang.Pair;
import com.datalinkx.common.constants.MetaConstants;
import com.datalinkx.common.exception.DatalinkXServerException;
import com.datalinkx.common.result.StatusCode;
import com.datalinkx.common.utils.Base64Utils;
import com.datalinkx.common.utils.ConnectIdUtils;
import com.datalinkx.common.utils.JsonUtils;
import com.datalinkx.dataserver.bean.domain.DsBean;
import com.datalinkx.dataserver.bean.domain.JobBean;
import com.datalinkx.dataserver.bean.vo.PageVo;
import com.datalinkx.dataserver.client.HttpConstructor;
import com.datalinkx.dataserver.controller.form.DsForm;
import com.datalinkx.dataserver.repository.DsRepository;
import com.datalinkx.dataserver.repository.JobRepository;
import com.datalinkx.dataserver.service.DsService;
import com.datalinkx.driver.dsdriver.DsDriverFactory;
import com.datalinkx.driver.dsdriver.IDsDriver;
import com.datalinkx.driver.dsdriver.IDsReader;
import com.datalinkx.driver.dsdriver.base.model.DbTableField;
import com.datalinkx.driver.dsdriver.base.model.DbTree;
import com.datalinkx.driver.dsdriver.esdriver.EsSetupInfo;
import com.datalinkx.driver.dsdriver.httpdriver.HttpSetupInfo;
import com.datalinkx.driver.dsdriver.kafkadriver.KafkaSetupInfo;
import com.datalinkx.driver.dsdriver.mysqldriver.MysqlSetupInfo;
import com.datalinkx.driver.dsdriver.oracledriver.OracleSetupInfo;
import com.datalinkx.driver.dsdriver.redisdriver.RedisSetupInfo;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.datalinkx.common.utils.IdUtils.genKey;


@Service
@Log4j2
public class DsServiceImpl implements DsService {

	@Autowired
	private DsRepository dsRepository;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private DataSource dataSource;


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


	private void checkConnect(DsBean dsBean) {
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
		String toType = Optional.ofNullable(MetaConstants.DsType.TYPE_TO_DB_NAME_MAP.get(dsBean.getType())).orElse("").toLowerCase();
		switch (toType) {
			case "mysql":
				MysqlSetupInfo mysqlSetupInfo = new MysqlSetupInfo();
				mysqlSetupInfo.setServer(dsBean.getHost());
				mysqlSetupInfo.setPort(dsBean.getPort());
				mysqlSetupInfo.setType(toType);
				mysqlSetupInfo.setUid(dsBean.getUsername());
				mysqlSetupInfo.setPwd(dsBean.getPassword());
				mysqlSetupInfo.setDatabase(dsBean.getDatabase());
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(mysqlSetupInfo));
			case "elasticsearch":
				EsSetupInfo esSetupInfo = new EsSetupInfo();
				esSetupInfo.setType(toType);
				esSetupInfo.setAddress(dsBean.getHost() + ":" + dsBean.getPort());
				esSetupInfo.setPwd(dsBean.getPassword());
				esSetupInfo.setUid(dsBean.getUsername());
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(esSetupInfo));
			case "oracle":
				OracleSetupInfo oracleSetupInfo = new OracleSetupInfo();
				oracleSetupInfo.setType(toType);
				oracleSetupInfo.setServer(dsBean.getHost());
				oracleSetupInfo.setPort(dsBean.getPort());
				oracleSetupInfo.setPwd(dsBean.getPassword());
				oracleSetupInfo.setUid(dsBean.getUsername());

				Map configMap = JsonUtils.toObject(dsBean.getConfig(), Map.class);
				if (configMap.containsKey("sid")) {
					oracleSetupInfo.setConnectType("SID");
					oracleSetupInfo.setSid((String) configMap.get("sid"));
				} else {
					oracleSetupInfo.setConnectType("SERVERNAME");
					oracleSetupInfo.setSid((String) configMap.get("servername"));
				}

				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(oracleSetupInfo));
			case "redis":
				RedisSetupInfo redisSetupInfo = new RedisSetupInfo();
				redisSetupInfo.setDatabase(Integer.parseInt(StringUtils.hasLength(dsBean.getDatabase()) ? dsBean.getDatabase() : "0"));
				redisSetupInfo.setHost(dsBean.getHost());
				redisSetupInfo.setPort(dsBean.getPort());
				redisSetupInfo.setPwd(dsBean.getPassword());
				redisSetupInfo.setType(toType);
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(redisSetupInfo));
			case "http":
				HttpSetupInfo httpSetupInfo = JsonUtils.toObject(dsBean.getConfig(), HttpSetupInfo.class);
				Pair<String, Integer> host = HttpConstructor.checkUrlFormat(httpSetupInfo.getUrl());
				httpSetupInfo.setHost(host.getKey());
				httpSetupInfo.setPort(host.getValue());
				httpSetupInfo.setType(toType);
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(httpSetupInfo));
			case "kafka":
				KafkaSetupInfo kafkaSetupInfo = new KafkaSetupInfo();
				kafkaSetupInfo.setServer(dsBean.getHost());
				kafkaSetupInfo.setPort(dsBean.getPort());
				kafkaSetupInfo.setType(toType);
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(kafkaSetupInfo));
			default:
				Map<String, Object> map = new HashMap<>();
				map.put("type", MetaConstants.DsType.TYPE_TO_DB_NAME_MAP.get(dsBean.getType()));
				return ConnectIdUtils.encodeConnectId(JsonUtils.toJson(map));
		}
	}


    public PageVo<List<DsBean>> dsPage(DsForm.DataSourcePageForm dataSourcePageForm) {
		PageRequest pageRequest = PageRequest.of(dataSourcePageForm.getPageNo() - 1, dataSourcePageForm.getPageSize());
		Page<DsBean> dsBeans = dsRepository.pageQuery(pageRequest, dataSourcePageForm.getName(), dataSourcePageForm.getType());
		PageVo<List<DsBean>> result = new PageVo<>();
		result.setPageNo(dataSourcePageForm.getPageNo());
		result.setPageSize(dataSourcePageForm.getPageSize());
		result.setData(dsBeans.getContent());
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
		DsBean dsBean = dsCheck.orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS, "ds not exist"));
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
		DsBean dsBean = dsRepository.findByDsId(dsId).orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
		List<String> tableList = new ArrayList<>();
		try {
			IDsDriver dsDriver = DsDriverFactory.getDriver(getConnectId(dsBean));
			if (dsDriver instanceof IDsReader) {
				IDsReader dsReader = (IDsReader) dsDriver;
				tableList = dsReader.treeTable(dsBean.getDatabase(), dsBean.getSchema()).stream().map(DbTree::getName).collect(Collectors.toList());
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
		DsBean dsBean = dsRepository.findByDsId(dsId).orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
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
	public List<Map<String, Object>> getTableData(String dsId, String tableName) throws UnsupportedEncodingException {
		DsBean dsBean = dsRepository.findByDsId(dsId).orElseThrow(() -> new DatalinkXServerException(StatusCode.DS_NOT_EXISTS));
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
			String sql = "SELECT * FROM " + tableName;
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(sql);

			// 获取列信息
			ResultSetMetaData metaData = resultSet.getMetaData();
			int columnCount = metaData.getColumnCount();

			// 将数据转换为适合图表的数据格式
			while (resultSet.next()) {
				Map<String, Object> row = new HashMap<>();
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
