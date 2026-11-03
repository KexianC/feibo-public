import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Row,
  Col,
  Button,
  Input,
  Space,
  Modal,
  Form,
  Upload,
  message,
  Pagination,
  Image,
  Popconfirm,
  Spin,
  Empty,
  DatePicker,
  Descriptions,
  Progress,
  Table
} from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  InboxOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { UploadFile, UploadProps } from 'antd/es/upload';
import type { RangePickerProps } from 'antd/es/date-picker';
import dayjs from 'dayjs';
import {
  BackgroundImageService,
  type BackgroundImage,
  type PageQueryParams,
  type BackgroundImageParams
} from '../../services/backgroundImageService';

const { Search } = Input;
const { RangePicker } = DatePicker;
const { Dragger } = Upload;

/**
 * 背景管理组件
 */
const BackgroundManage: React.FC = () => {
  // 状态管理
  const [images, setImages] = useState<BackgroundImage[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const editInFlight = useRef(false);
  const [selectedImage, setSelectedImage] = useState<BackgroundImage | null>(null);
  
  // 分页状态
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0
  });

  // 搜索和筛选状态（与实际API字段对齐）
  const [searchParams, setSearchParams] = useState<PageQueryParams>({
    page: 1,
    pageSize: 12
  });

  // 模态框状态
  const [previewVisible, setPreviewVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [editVisible, setEditVisible] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);

  // 表单实例
  const [editForm] = Form.useForm();
  const [uploadForm] = Form.useForm();

  // 上传状态
  const [uploadProgress, setUploadProgress] = useState(0);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  /**
   * 获取背景图片列表（带调试日志，兼容不同响应结构）
   * @param params 分页与筛选参数（title、isShow、startDate、endDate）
   */
  const fetchGeneration = useRef(0);
  const fetchImages = async (params: PageQueryParams = searchParams) => {
    const generation = ++fetchGeneration.current;
    try {
      setLoading(true);
      const response = await BackgroundImageService.getBackgroundImagesPage(params);
      if (generation !== fetchGeneration.current) return;

      // 直出分页结构：{ records, total, size, current, pages }
      const pageData = response as unknown as { records: BackgroundImage[]; total: number; size: number; current: number; pages: number };

      if (pageData && Array.isArray(pageData.records)) {
        // 清洗 indexImage，去掉可能存在的反引号和首尾空格
        const cleanedRecords = pageData.records.map((img) => ({
          ...img,
          indexImage: String(img.indexImage || '').replace(/`/g, '').trim()
        }));

        setImages(cleanedRecords);
        setPagination({
          current: pageData.current,
          pageSize: pageData.size,
          total: pageData.total
        });
      } else {
        console.error('[BackgroundManage] 分页响应不是预期结构:', response);
        message.error('获取图片列表失败：响应格式异常');
      }
    } catch (error: any) {
      console.error('获取图片列表失败:', error);
      message.error(`获取图片列表失败：${error?.message || '未知错误'}`);
    } finally {
      if (generation === fetchGeneration.current) setLoading(false);
    }
  };

  /**
   * 搜索标题
   * @param value 标题关键字
   */
  const handleSearch = (value: string) => {
    const newParams = {
      ...searchParams,
      page: 1,
      title: value || undefined
    };
    setSearchParams(newParams);
    fetchImages(newParams);
  };

  /**
   * 状态筛选（1显示，0隐藏）
   * @param status 显示状态
   */

  /**
   * 日期范围筛选
   * @param dates 开始与结束日期
   */
  const handleDateRangeChange: RangePickerProps['onChange'] = (dates) => {
    const newParams = {
      ...searchParams,
      page: 1,
      startDate: dates?.[0]?.format('YYYY-MM-DD'),
      endDate: dates?.[1]?.format('YYYY-MM-DD')
    };
    setSearchParams(newParams);
    fetchImages(newParams);
  };

  /**
   * 分页变化
   * @param page 页码
   * @param pageSize 每页大小
   */
  const handlePageChange = (page: number, pageSize?: number) => {
    const newParams = {
      ...searchParams,
      page,
      pageSize: pageSize || searchParams.pageSize
    };
    setSearchParams(newParams);
    fetchImages(newParams);
  };

  /**
   * 图片预览
   * @param image 选中图片
   */
  const handlePreview = (image: BackgroundImage) => {
    setSelectedImage(image);
    setPreviewVisible(true);
  };

  /**
   * 查看详情
   * @param image 选中图片
   */
  const handleViewDetail = (image: BackgroundImage) => {
    setSelectedImage(image);
    setDetailVisible(true);
  };

  /**
   * 进入编辑
   * @param image 选中图片
   */
  const handleEdit = (image: BackgroundImage) => {
    setSelectedImage(image);
    editForm.setFieldsValue({
      title: image.title
    });
    setEditVisible(true);
  };

  /**
   * 删除图片
   * @param id 图片ID
   */
  const handleDelete = async (id: string) => {
    try {
      await BackgroundImageService.deleteBackgroundImage(id);
      message.success('删除成功');
      fetchImages();
    } catch (error) {
      console.error('删除失败:', error);
      message.error('删除失败');
    }
  };

  /**
   * 编辑提交
   * @param values 表单值（title、sort、isShow）
   */
  /**
   * 编辑保存（兼容直出/包装响应），成功后自动刷新
   * @param values 表单值（title、sort、isShow）
   */
  const handleEditSubmit = async (values: Partial<BackgroundImageParams>) => {
    if (!selectedImage || editInFlight.current) return;
    editInFlight.current = true;
    setEditLoading(true);
    try {
      await BackgroundImageService.updateBackgroundImage(selectedImage.id, values);
      message.success('更新成功');
      setEditVisible(false);
      fetchImages();
    } catch (error) {
      console.error('更新失败:', error);
      message.error('更新失败');
    } finally {
      editInFlight.current = false;
      setEditLoading(false);
    }
  };

  /**
   * 上传文件（带进度）
   * @param file 文件对象
   */

  /**
   * 上传提交（使用新接口：上传并保存）
   * @param values 表单值（title、isShow）
   */
  const uploadInFlight = useRef(false);
  const handleUploadSubmit = async (values: any) => {
    if (uploadInFlight.current) return;
    if (!fileList.length) { message.error('请选择要上传的图片'); return; }
    uploadInFlight.current = true;
    setUploadLoading(true);
    let completed = 0;
    try {
      for (const item of fileList) {
        const file = item.originFileObj;
        if (!file) throw new Error('文件内容不可用，请重新选择图片');
        await BackgroundImageService.uploadAndSave(file, { title: values.title || item.name });
        completed++;
        setFileList(current => current.filter(pending => pending.uid !== item.uid));
        setUploadProgress(Math.round(completed / fileList.length * 100));
      }
      message.success('已上传并保存 ' + completed + ' 张图片');
      setUploadVisible(false);
      uploadForm.resetFields();
    } catch (error) {
      message.error('已保存 ' + completed + ' 张，剩余图片未完成：' + (error instanceof Error ? error.message : '上传失败'));
    } finally {
      uploadInFlight.current = false;
      setUploadLoading(false);
      setUploadProgress(0);
      fetchImages();
    }
  };

  /**
   * 上传组件配置
   */
  const uploadProps: UploadProps = {
    name: 'file',
    multiple: true,
    fileList,
    beforeUpload: (file) => {
      const isImage = file.type.startsWith('image/');
      if (!isImage) {
        message.error('只能上传图片文件');
        return Upload.LIST_IGNORE;
      }
      const isLt10M = file.size / 1024 / 1024 < 10;
      if (!isLt10M) {
        message.error('图片大小不能超过10MB');
        return Upload.LIST_IGNORE;
      }
      return false; // 阻止自动上传
    },
    onChange: (info) => {
      setFileList(info.fileList);
    },
    onDrop: (e) => {
      console.log('Dropped files', e.dataTransfer.files);
    }
  };

  /**
   * 状态标签（1显示，0隐藏）
   */

  // 组件挂载时获取数据
  useEffect(() => {
    fetchImages();
  }, []);

  return (
    <div className="background-manage">
      <Card>
        {/* 搜索和筛选区域 */}
        <Row gutter={[16, 16]} className="mb-4">
          <Col xs={24} sm={12} md={8} lg={6}>
            <Search
              placeholder="搜索标题"
              allowClear
              onSearch={handleSearch}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <RangePicker
              style={{ width: '100%' }}
              onChange={handleDateRangeChange}
              placeholder={['创建开始日期', '创建结束日期']}
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Space>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setUploadVisible(true)}
              >
                上传图片
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => fetchImages()}
              >
                刷新
              </Button>
            </Space>
          </Col>
        </Row>

        <Spin spinning={loading}>
          {images.length > 0 ? (
            <Table
              rowKey="id"
              dataSource={images}
              pagination={false}
              columns={[
                { title: 'ID', dataIndex: 'id', key: 'id', width: 120 },
                { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
                { title: '索引图', dataIndex: 'indexImage', key: 'indexImage', width: 160, render: (u: string) => (u ? <Image src={u} width={140} height={88} style={{ objectFit: 'cover' }} /> : '-') },

                { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180, render: (t: string) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-') },
                {
                  title: '操作', key: 'action', fixed: 'right', width: 220,
                  render: (_: any, record: any) => (
                    <Space size="small">
                      <Button type="link" icon={<EyeOutlined />} onClick={() => handlePreview(record)}>预览</Button>
                      <Button type="link" icon={<SearchOutlined />} onClick={() => handleViewDetail(record)}>详情</Button>
                      <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
                      <Popconfirm title="确定要删除这张图片吗？" onConfirm={() => handleDelete(record.id)}>
                        <Button type="link" icon={<DeleteOutlined />} danger>删除</Button>
                      </Popconfirm>
                    </Space>
                  )
                }
              ]}
              scroll={{ x: 1100 }}
            />
          ) : (
            <Empty description="暂无背景图片" />
          )}
        </Spin>

        {/* 分页 */}
        {pagination.total > 0 && (
          <div className="flex justify-center mt-6">
            <Pagination
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={pagination.total}
              showSizeChanger
              showQuickJumper
              showTotal={(total, range) =>
                `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
              }
              onChange={handlePageChange}
            />
          </div>
        )}
      </Card>

      {/* 图片预览模态框 */}
      <Modal
        open={previewVisible}
        title="图片预览"
        footer={null}
        onCancel={() => setPreviewVisible(false)}
        width={800}
        centered
      >
        {selectedImage && (
          <div className="text-center">
            <Image
              src={selectedImage.indexImage}
              alt={selectedImage.title}
              style={{ maxWidth: '100%' }}
            />
            <div className="mt-4 text-gray-600">
              <div>{selectedImage.title}</div>
            </div>
          </div>
        )}
      </Modal>

      {/* 图片详情模态框 */}
      <Modal
        open={detailVisible}
        title="图片详情"
        footer={null}
        onCancel={() => setDetailVisible(false)}
        width={600}
      >
        {selectedImage && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="标题">{selectedImage.title}</Descriptions.Item>

            <Descriptions.Item label="创建时间">{dayjs(selectedImage.createTime).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
            <Descriptions.Item label="图片预览">
              <Image src={selectedImage.indexImage} alt={selectedImage.title} style={{ maxWidth: 200 }} />
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* 编辑图片模态框 */}
      <Modal
        open={editVisible}
        title="编辑图片"
        confirmLoading={editLoading}
        onOk={() => editForm.submit()}
        onCancel={() => { if (!editInFlight.current) setEditVisible(false); }}
        okText="保存"
        cancelText="取消"
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditSubmit}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}> 
            <Input placeholder="请输入标题" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 上传图片模态框 */}
      <Modal
        open={uploadVisible}
        title="上传图片"
        onOk={() => uploadForm.submit()}
        onCancel={() => {
          if (uploadInFlight.current) return;
          setUploadVisible(false);
          setFileList([]);
          uploadForm.resetFields();
          setUploadProgress(0);
        }}
        okText="上传"
        cancelText="取消"
        confirmLoading={uploadLoading}
        width={600}
      >
        <Form form={uploadForm} layout="vertical" onFinish={handleUploadSubmit}>
          <Form.Item label="选择图片">
            <Dragger {...uploadProps} style={{ marginBottom: 16 }}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽图片到此区域上传</p>
              <p className="ant-upload-hint">支持单个或批量上传，文件大小不超过 10MB</p>
            </Dragger>
          </Form.Item>

          {uploadProgress > 0 && uploadProgress < 100 && (
            <Form.Item>
              <Progress percent={uploadProgress} />
            </Form.Item>
          )}

          <Form.Item name="title" label="标题"> 
            <Input placeholder="留空则使用文件名" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default BackgroundManage;
