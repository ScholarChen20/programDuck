# Sa-token介绍

Sa-token是一个轻量级的Java框架，它是一款功能完善、性能卓越、易于集成的Java权限认证和访问控制框架。基于
Sa-token实现同端登录检测，使用Bloom Filter验证用户身份有效性，防止缓存穿透问题。

Bloom Filter 是一种空间效率极高的概率型数据结构，用于判断一个元素是否可能存在于一个集合中。我们用它来快速判断一个 Token 是
否可能有效（即可能在缓存或DB中存在）。优点是查询速度快（O(k)）、占用内存极小（相比存储所有Token）。
Bloom Filter 的作用是拦截掉绝大部分明显无效的请求，防止它们穿透到缓存或DB层。

系统启动或定时任务时，将当前所有有效Token（或Token对应的用户ID/关键标识）加载到 Bloom Filter 中。更新策略：
* 用户登录： 生成新Token后，立即添加到 Bloom Filter。
* 用户登出/Token失效： Bloom Filter 不支持删除！所以不能直接从BF中移除。常用策略：
1. 定期重建： 设置一个较短的时间窗口（如每5分钟），重新从持久化存储（DB或最新缓存）加载所有有效Token构建新的 Bloom Filter 替换旧的。
2. 计数型Bloom Filter（CBF）： 使用更复杂的变种，支持删除（但空间开销更大）。在我们的场景下，定期重建通常是更简单高效的选择。


    public static String getRequestDevice(HttpServletRequest request) {
        String userAgentStr = request.getHeader(Header.USER_AGENT.toString());
        // 使用 Hutool 解析 UserAgent
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        ThrowUtils.throwIf(userAgent == null, ErrorCode.OPERATION_ERROR, "非法请求");
        // 默认值是 PC
        String device = "pc";
        // 是否为小程序
        if (isMiniProgram(userAgentStr)) {
            device = "miniProgram";
        } else if (isPad(userAgentStr)) {
            // 是否为 Pad
            device = "pad";
        } else if (userAgent.isMobile()) {
            // 是否为手机
            device = "mobile";
        }
        return device;
    }

    private static boolean isMiniProgram(String userAgentStr) {
        return StrUtil.containsIgnoreCase(userAgentStr, "MicroMessenger")
                && StrUtil.containsIgnoreCase(userAgentStr, "MiniProgram");
    }

    private static boolean isPad(String userAgentStr) {
        boolean isIpad = StrUtil.containsIgnoreCase(userAgentStr, "iPad");
        boolean isAndroidTablet = StrUtil.containsIgnoreCase(userAgentStr, "Android")
                && !StrUtil.containsIgnoreCase(userAgentStr, "Mobile");
        return isIpad || isAndroidTablet;
    }

# ElasticSearch+IK分词器
ElasticSearch是一个开源的搜索引擎，它基于Lucene，是一个分布式全文搜索引擎。IK分词器是一个开源的基于Java开发的轻量级中文分词器
，它基于词典和正则表达式，并集成了词频、词性标注、拼音、词干提取等功能。

MySQL LIKE 查询无法利用索引，会导致全表扫描，数据量大时查询极慢。IK Analyzer 是一个强大的中文分词器。
* 智能分词： 能将句子切分成有意义的词语
* 支持词典扩展： 可以添加自定义词库
* 停用词过滤： 过滤掉无意义的词
* 选择原因： 它是中文领域最成熟、最常用的 Elasticsearch 分词器之一，社区活跃，效果良好，能满足中文技术内容检索的需求。


    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        // todo 取消注释开启 ES（须先配置 ES）
        // 查询 ES
         Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
        // 查询数据库（作为没有 ES 的降级方案）
         return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }


# Redis BitMap+Redisson分布式锁
实现用户年度刷题记录的统计，相比数据库存储节约100倍空间。通过本地缓存+返回值优化，平均响应时间从2241ms减少到11ms。

我们为每个用户在 Redis 中创建一个 BitMap Key。这个 BitMap 的每一位（bit）代表用户在某一天是否有刷题记录。Offset 通常设计为日期偏移量。

Redisson 提供了强大的、分布式的、线程安全的 Java 对象操作接口和分布式锁。封装BitMap操作，简化代码。保证原子性，当多个线程/实例同时尝试更新同一个用户的同一位（同一天）时，
Redisson 的分布式锁（RLock）或它封装的原子操作可以保证并发更新的正确性，防止数据错乱。


    public boolean addUserSignIn(long userId) {  // 添加用户签到
        LocalDate date = LocalDate.now();
        String key = RedisConstant.getUserSignInRedisKey(date.getYear(), userId);
        // 获取 Redis 的 BitMap
        RBitSet signInBitSet = redissonClient.getBitSet(key);
        // 获取当前日期是一年中的第几天，作为偏移量（从 1 开始计数）
        int offset = date.getDayOfYear();
        // 查询当天有没有签到
        if (!signInBitSet.get(offset)) {
            // 如果当前未签到，则设置
            signInBitSet.set(offset, true);
        }
        // 当天已签到
        return true;
    }

    public List<Integer> getUserSignInRecord(long userId, Integer year) {  // 获取用户签到记录
        if (year == null) {
            LocalDate date = LocalDate.now();
            year = date.getYear();// 获取当前年份
        }
        String key = RedisConstant.getUserSignInRedisKey(year, userId);
        // 获取 Redis 的 BitMap
        RBitSet signInBitSet = redissonClient.getBitSet(key); // 获取 Redis 的 BitMap
        // 加载 BitSet 到内存中，避免后续读取时发送多次请求
        BitSet bitSet = signInBitSet.asBitSet();
        // 统计签到的日期
        List<Integer> dayList = new ArrayList<>();
        // 从索引 0 开始查找下一个被设置为 1 的位
        int index = bitSet.nextSetBit(0);
        while (index >= 0) {
            dayList.add(index);
            // 继续查找下一个被设置为 1 的位
            index = bitSet.nextSetBit(index + 1);
        }
        return dayList;
    }


# HotKey介绍

### HotKey 解决的问题
对任意突发性的无法预先感知的热点数据，包括并不限于热点数据（如突发大量请求同一个商品）、热用户（如恶意爬虫刷子）、热接口（突发海量请求同一个接口）等，进行毫秒级精准探测到。
然后对这些热数据、热用户等，推送到所有服务端JVM内存中，以大幅减轻对后端数据存储层的冲击，并可以由使用者决定如何分配、使用这些热key（譬如对热商品做本地缓存、对热用户进行拒绝访问
、对热接口进行熔断或返回默认值）。
总结一句话就是**热点缓存，防止穿透**

### Hotkey 如何检测和应对热Key
1. 客户端探测（Worker）： 每个应用实例内置一个Worker，实时统计本地请求的Key频率。
2. 上报与决策（Dashboard）： Worker将统计到的热点Key候选上报给中心控制台（Dashboard）。 Dashboard根据全局聚合的统计信息（频率、来源分布）和预设规则（如QPS阈值、时间窗口）判断一个Key是否为真正的热Key。
3. 热Key推送： 一旦确认热Key，Dashboard会立即将热Key及其值（或加载策略）推送给所有Worker。
4. 本地缓存： Worker收到热Key后，将其缓存到应用本地内存（如Caffeine）。后续对该Key的请求，直接由本地缓存响应，不再访问Redis，更不会穿透到DB。本地缓存通常设置较短的过期时间（如几秒）并监听Dashboard的更新推送。

### Hotkey 和 Redis 自身缓存有什么区别
Redis 是集中式缓存，所有实例共享。访问热Key仍需经过网络IO，在极端高并发下，Redis 单点或集群也可能成为瓶颈。Hotkey 的核心
是将热Key进一步下沉到离应用最近的本地内存，实现零网络IO访问，彻底消除对Redis和DB的冲击，并提供更高的吞吐量和极低的延迟。

### 实现过程
通过客户端主动上报数据到计算节点（worker)，通过获取配置中心的规则配置最终计算热点数据。通过计算节点回推给所有的客户端，本地JVM缓存起来

    private String etcdServer = "http://127.0.0.1:2379"; // Etcd 服务器地址

    private String appName = "app";  // 应用名称 
 
    private int caffeineSize = 10000; // 本地缓存最大数量

    private long pushPeriod = 1000L; // 推送间隔时间

    @Bean  //hotkey初始化
    public void initHotkey() {
        ClientStarter.Builder builder = new ClientStarter.Builder(); // 创建热key启动器
        ClientStarter starter = builder.setAppName(appName) // 设置应用名称
                .setCaffeineSize(caffeineSize) // 设置本地缓存最大数量
                .setPushPeriod(pushPeriod) // 设置批量推送 key 的间隔时间
                .setEtcdServer(etcdServer) // 设置 Etcd 服务器完整地址
                .build(); // 启动热key
        starter.startPipeline(); // 启动热key流水线
    }

    //判断rekey
    public Object a(String s) {
         if (JdHotKeyStore.isHotKey(”store_" + s)) {
             logger.error("isHot");
         } else {
             logger.error("noHot");
         } 
          return 1;
    }

    //获取热key value                                 
    public String get(String key) {
        Object object = JdHotKeyStore.getValue(key);
        //如果已经缓存过了
        if (object != null) {
             System.out.println("is hot key");
             return object.toString();
        } else {
            //底层获取
             String value = getFromRedis(key);
            //判断热key，塞入值
            JdHotKeyStore.smartSet(key, value);
            return value;
         }
     }

# AI请求客户端：

# 调用流程是怎样的？
1. 管理员输入： 管理员提供一个题目的文本描述（可能包括标题、描述、示例等）
2. 构造Prompt： 后端构造一个清晰的Prompt发给DeepSeek API，例如：“请为以下编程题目生成合适的技术标签和难度等级：[题目描述]。请仅返回JSON格式的结果，包含字段：**tags** (字符串数组，如 ['数组', '哈希表', '双指针']) 和 **difficulty** (字符串，可选值：'简单', '中等', '困难')。”
3. 调用API & 解析： 调用 DeepSeek API，获取其生成的文本响应。解析响应中的JSON数据（或处理其返回的自然语言文本提取所需信息）。
4. 校验与存储： 对生成的标签和难度进行人工审核或简单逻辑校验。校验通过后，将题目信息（包含生成的标签和难度）保存到数据库（MySQL），并异步索引到Elasticsearch。

# 如何处理API的响应慢或失败？
1. 异步处理： 将题目标签生成任务放入消息队列（如RabbitMQ, Kafka）。后台Worker消费队列任务并调用API。这样不会阻塞管理员操作。
2. 重试机制： 在Worker调用API失败时，进行有限次数的指数退避重试。 
3. 超时控制： 设置合理的API调用超时时间。 
4. 降级处理： 如果API持续不可用或响应超时，可以降级为管理员手动输入标签和难度，或者使用一个简单的基于规则的本地分类器（效果差些）。 
5. 监控告警： 监控API调用成功率、延迟等指标，异常时告警。


调用DeepSeek-V3 API 实现管理员端AI生成题目和题解，快速丰富平台的数目数量。
1. 创建一个AI请求客户端，用于发送AI请求，并返回结果。
2. 客户端需要实现AI请求的序列化和反序列化，并通过HTTP协议发送给AI服务端。
3. AI服务端接收到请求后，进行预处理，并调用相应的AI模型进行处理。
4. AI服务端返回结果给客户端。
5. 客户端接收到结果后，进行结果的反序列化，并进行业务处理。


    public ArkService aiService() {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3") // Ark 默认地址
                .apiKey(apiKey) // 设置 ApiKey
                .build(); // 创建 ArkService 客户端
        return service;
    }


    public boolean aiGenerateQuestions(String questionType, int number, User user) {
        if (ObjectUtil.hasEmpty(questionType, number, user)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        // 1. 定义系统 Prompt
        String systemPrompt = "你是一位专业的程序员面试官，你要帮我生成 {数量} 道 {方向} 面试题，要求输出格式如下：\n" +
                "\n" +
                "1. 什么是 Java 中的反射？\n" +
                "2. Java 8 中的 Stream API 有什么作用？\n" +
                "3. xxxxxx\n" +
                "\n" +
                "除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出上面的列表。\n" +
                "\n" +
                "接下来我会给你要生成的题目{数量}、以及题目{方向}\n";
        // 2. 拼接用户 Prompt
        String userPrompt = String.format("题目数量：%s, 题目方向：%s", number, questionType);
        // 3. 调用 AI 生成题目
        String answer = aiManager.doChat(systemPrompt, userPrompt);
        // 4. 对题目进行预处理
        // 按行拆分
        List<String> lines = Arrays.asList(answer.split("\n"));
        // 移除序号和 `
        List<String> titleList = lines.stream()
                .map(line -> StrUtil.removePrefix(line, StrUtil.subBefore(line, " ", false))) // 移除序号
                .map(line -> line.replace("`", "")) // 移除 `
                .collect(Collectors.toList());
        // 5. 保存题目到数据库中
        List<Question> questionList = titleList.stream().map(title -> {
            Question question = new Question();
            question.setTitle(title);
            question.setUserId(user.getId());
            question.setTags("[\"待审核\"]");
            // 优化点：可以并发生成
            question.setAnswer(aiGenerateQuestionAnswer(title));
            return question;
        }).collect(Collectors.toList());
        boolean result = this.saveBatch(questionList);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存题目失败");
        }
        return true;
    }


    public String doChat(String systemPrompt, String userPrompt, String model) {
        // 构造消息列表
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build(); // 系统提示
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build(); // 用户问题
        messages.add(systemMessage); // 系统提示
        messages.add(userMessage); // 用户问题
        return doChat(messages, model);
    }

    public String doChat(List<ChatMessage> messages) {
        return doChat(messages, DEFAULT_MODEL);
    }

    public String doChat(List<ChatMessage> messages, String model) {
        // 构造请求，默认模型，消息列表，默认超时时间
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder() //                .model("deepseek-v3-241226")
                .model(model)
                .messages(messages)
                .build();
        // 调用接口发送请求
        List<ChatCompletionChoice> choices = aiService.createChatCompletion(chatCompletionRequest).getChoices();
        if (CollUtil.isNotEmpty(choices)) { /// 响应不为空，返回结果
            return (String) choices.get(0).getMessage().getContent(); // 返回结果，默认返回字符串，可根据实际情况修改
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");
    //        // shutdown service after all requests is finished
    //        aiService.shutdownExecutor();
    }

# 为什么选择 Sa-Token 而不是 Spring Security？
1. Sa-Token 以简单易用、功能强大、轻量级著称，尤其在国内社区流行。可能基于以下考虑：

2. 学习曲线： Sa-Token 的API设计更符合国人习惯，学习成本相对较低。

3. 开箱即用： 对常见需求（登录/注销、权限验证、踢人下线、同端互斥登录、分布式会话）提供了更简洁直接的实现。

4. 轻量： 依赖较少，对项目侵入性小。
