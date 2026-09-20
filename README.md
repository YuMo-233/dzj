# 打字机文本样式 typewriter_text（NeoForge 1.21.1）

逐字显示不是新的组件类型，也不新加指令，而是**原版文本组件样式上的一个字段**：

    {"text":"你好，世界","typewriter":{"time":"0.2s"}}

凡是能写原版样式字段（`color`、`bold`、`clickEvent`、`hoverEvent`、`insertion`……）的地方
都能写 `typewriter`；文字内容完全走原版组件（`text`、`translate`、`score`、`nbt`、
`selector`、`keybind`……），`extra` 嵌套子组件照常工作。

## 字段

`typewriter` 是一个对象，里面的字段都可以省略：

| 字段 | 含义 |
|---|---|
| `time` | **出字速度：相邻两个字符间隔多久**，数值越小出字越快。数字按 tick 计（整数），字符串可带单位：`"0.25s"`（秒，可小数）、`"4t"`（tick）；省略时每字符 2 tick |
| `command` | **每打出一个字符触发一次**的指令，不带前导斜杠。占位符 `%c` 当前字符、`%i` 序号、`%n` 总字数，上限 512 字符 |
| `session` | 可省略，省略时自动生成并随组件一起序列化（双端一致，用于配对指令回报） |

`time` 范围 **1–200 tick**（最快每秒 20 字，最慢每字 10 秒）；不带单位的数字只接受整数，
`{"time":0.5}` 会被拒掉，要写 `{"time":"0.5s"}`。样式按原版语义向下继承——父组件写了
`typewriter`，它和它的 `extra` 子组件会**连成一整段文字一起逐字打出**。

`command` 以**观看者身份、权限封顶 2 级、完全静默**执行：指令自身给执行者的回显
（比如 `playsound` 的“已播放声音…”提示）会被抑制，聊天栏里不会冒出任何系统提示。

## 用法示例（把下面的内容直接写进指令参数）

逐字打字，每秒 5 个字（每个字 0.2 秒）：

    tellraw @a {"text":"任务开始：在废墟里找到三块红石。","color":"gold","typewriter":{"time":"0.2s"}}

同样的速度也可以直接按 tick 写（0.2 秒 = 4 tick），两种写法等价：

    tellraw @a {"text":"任务开始：在废墟里找到三块红石。","color":"gold","typewriter":{"time":4}}

和原版组件混用（前半截普通文本立刻显示，后半截逐字打）：

    tellraw @a ["【公告】 ",{"text":"服务器将于十分钟后重启","color":"red","bold":true,"typewriter":{"time":4}}]

写成一段两句话的串烧——`typewriter` 写在父组件上，子组件跟着一起打：

    tellraw @a {"text":"任务开始：","color":"gold","typewriter":{"time":"0.15s"},"extra":[{"text":"在废墟里找到三块红石。"},{"text":"限时十分钟。","color":"red"}]}

每打一个字符响一声（打字机音效，这里打得很急）：

    tellraw @a {"text":"叮叮叮……","typewriter":{"time":"0.1s","command":"playsound minecraft:block.note_block.hat master @s ~ ~ ~ 0.15 1.6"}}

标题、动作栏、bossbar 同样可用：

    title @a title {"text":"BOSS 登场","color":"dark_red","bold":true,"typewriter":{"time":"0.25s"}}
    title @a actionbar {"text":"正在解析古代铭文……","typewriter":{"time":3}}
    bossbar add quest {"text":"古代铭文","typewriter":{"time":4}}

写进数据组件（物品名、lore 也会逐字显示）：

    give @a netherite_sword[custom_name='{"text":"会低语的剑","color":"light_purple","typewriter":{"time":"0.25s"}}']

也可以用翻译键取词（`with` 里的参数会展平成文字一起参与逐字显示）：

    tellraw @a {"translate":"chat.type.text","with":["Steve","醒了。"],"typewriter":{"time":4}}

## 附加指令（可选，打字机本身不依赖它）

    tw show <目标> <组件>     把组件发到目标玩家的 action bar
    tw stop <目标>            让该玩家正在播放的打字机立刻收尾

`typewriter` 是 `tw` 的同义长名。

## 从 r7 升级

r7 的写法（组件内容类型）**在 r8 不再支持**，按下表改写：

| r7 | r8 及以后 |
|---|---|
| `{"type":"typewriter_text:typewriter","text":"…","time":80,"color":"gold"}` | `{"text":"…","color":"gold","typewriter":{"time":"0.2s"}}` |

`translate`/`with` 同理：不再是组件类型，而是随便挑一个原版组件（`text`、`translate`……）
再挂上 `typewriter` 字段。

**r9 起 `time` 的含义变了**：r8 里它是"整段总时长"，r9 起是"每字符间隔"，`interval`
字段同时被删掉（见 [0005](docs/adr/0005-出字速度按每字符tick计并支持单位后缀.md)）。
同一个 `{"time":80}`，改前是"整段 4 秒打完"，改后是"每个字 4 秒"，写旧值时留意一下。

## 模组开发者 API

    Component msg = TypewriterSpec.builder()
            .text("任务开始……")
            .time(60)
            .command("playsound minecraft:block.note_block.hat master @s ~ ~ ~ 0.2 1.5")
            .withStyle(ChatFormatting.GOLD)
            .build();
    player.sendMessage(msg, false);

产出的就是普通 `Component`，可以塞进任何原版接口。

## 实现要点（排错用）

- **样式字段怎么接进原版的**：`StyleSerializerMixin` 在 `Style$Serializer` 的类初始化里，
  把原本组装 `MAP_CODEC` 的那次 `RecordCodecBuilder.mapCodec(...)` 换成包装版（原版字段 +
  可选的 `typewriter`）。原版的 `CODEC` 与网络用的 `TRUSTED_STREAM_CODEC` 都由这个
  MAP_CODEC 派生，所以 JSON、NBT 数据组件、网络包三条通道的字段集合天然一致，不用事后反射改
  final 字段。组件 JSON 里样式字段与内容本来就摊平在同一层
  （`ComponentSerialization` 用 `Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)`），
  因此 `{"text":"你好","typewriter":{…}}` 直接就能解析。
- **绝不往 `Style.EMPTY` 上写数据**：原版 `Style.create` 在字段全空时返回 `Style.EMPTY`
  这个全局单例，而 `Style` 没有任何公开的构造入口——每个 `withXxx(null)` 都会被
  `checkEmptyAfterChange` 再次折回单例（它判的是 `equals(EMPTY)` 而非 `isEmpty()`）。
  所以挂数据前一律用 `TypewriterStyleCodec.copyOf` 反射出一份“字段全空但不是单例”的副本；
  万一反射不可用，就退化成普通文本，宁可不动画也不污染全局。
- **渲染挂载点**：`MutableComponentMixin` 给 `MutableComponent` 补上
  `visit(StyledContentConsumer, Style)` 的实现，把文字按进度切片后再交给原版排版器。
  这是“组件 → 可绘制文字”的唯一出口，标题/动作栏/聊天栏/工具提示/bossbar/物品名一次性全覆盖。
  同一 mixin 还旁路 `getVisualOrderText` 的实例缓存（原版一次计算终身复用），逐字效果才动得起来；
  没出现过打字机样式时先查全局标记短路，普通组件零额外开销。
- **聊天栏要主动重排**：聊天栏的行切分结果是按消息缓存的，动画期间每 tick 调一次
  `ChatComponent#refreshTrimmedMessages`（由 `ChatComponentAccessor` 暴露）才会重排。
  标题/动作栏/bossbar/物品名这些通道每帧重排，不需要额外处理。
- **进度在客户端渲染时刻计算**（首次读取即起笔），服务端读组件永远拿到全文，所以签名、
  长度校验、聊天过滤等原版逻辑不受影响。
- **`command` 的触发**：客户端只上报“会话 id + 第几个字符 + 该字符”，指令文本始终留在服务端
  登记表里（10 分钟 TTL，每人每秒 100 次上限），客户端无法注入任意命令；只有服务端线程解码出的
  样式才会登记，告示牌这类由客户端提交的组件不会登记，也就无法借道执行命令。
- **判等要额外比打字机身份**：原版 `Style.equals` 只比那十个原版字段，隐藏字段不在其中，
  于是“只差 `time`/`command`”的两个样式会被判为相等——物品堆叠（只保留一份数据）、聊天栏
  并成 “2x”。所以 `StyleMixin` 在 `equals` 返回 true 时补一次身份比较（速度 + 指令，见
  [0006](docs/adr/0006-打字机身份参与样式判等.md)）；`hashCode` 不动。
- **样式合并要带上状态**：`StyleMixin` 在 `Style.applyTo` 返回前补一次状态传递（`this` 胜过参数，
  与原版字段一致），这样 `withStyle(非空样式)` 之类的合并不会把打字机丢掉；逐字段另造样式的
  `withColor`/`applyFormat` 那些仍带不上，见下方已知限制。

## 已知限制

- **对已带打字机的组件做样式改写会丢效果**：`Style.applyTo` 一类（含 `withStyle(非空样式)`、
  `Component.visit` 的样式合并）已经补上了状态传递，但 `withColor`、`withBold`、`applyFormat`
  这些**逐字段另造 Style** 的方法盖不到。按本文档的写法（JSON 里直接写 `typewriter`）不会碰到，
  只有“先建好打字机组件、随后用这些方法再改写它的样式”才会。
- **堆叠判定只看速度与指令**：不同 `time` 或不同 `command` 的同名物品不再堆叠；同一段 JSON
  生成的两件物品仍然堆叠（`session` 是解码时随机生成的，不参与判等）。
