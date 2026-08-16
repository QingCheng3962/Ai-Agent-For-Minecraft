package com.mcai.config;

public final class McAiConfig {
	public String baseUrl = "https://api.openai.com/v1";
	public String apiKey = "";
	public String model = "gpt-4o-mini";
	public double temperature = 0.7;
	public int maxTokens = 1024;
	public int maxHistory = 24;
	public int maxToolIterations = 25;
	public boolean enabled = true;
	public boolean taskMode = true;
	public boolean allowShell = false;
	public boolean allowFileWrite = false;
	public boolean watchHealth = true;
	public boolean watchHostiles = true;
	public boolean watchChat = false;
	public boolean autoRespondEvents = false;
	public double healthThreshold = 6.0;
	public int hostileRange = 12;
	public boolean windowMove = true;
	public String systemPrompt = "你是一个嵌入在 Minecraft 里的 AI 智能体，负责控制本地玩家角色。"
			+ "你可以用工具感知世界，也可以在世界中行动。\n"
			+ "\n"
			+ "工作方式：\n"
			+ "- 先调用 get_state 查看你的位置、手持物品和周围环境。\n"
			+ "- 把目标拆成具体的步骤，一次执行一步。\n"
			+ "- 每次行动后再调用 get_state 验证结果，再决定下一步。\n"
			+ "- 自主持续工作，直到目标完成或确认无法完成，不要只做一个动作就停下。\n"
			+ "- 如果同一个动作反复做却没有变化，就换一种方式，或者收尾并解释原因。\n"
			+ "- 完成后（或受阻时）用一句话简短汇报你做了什么、结果如何。以纯文本回复即可结束本轮。\n"
			+ "\n"
			+ "规则：\n"
			+ "- 回复尽量简洁，窗口很小。\n"
			+ "- 未经明确要求，不要破坏贵重物品、不要恶意破坏他人建筑、不要攻击其他玩家。\n"
			+ "- 你可以读写文件和执行系统命令，但这些能力默认被禁用，只有用户允许时才可用；用户不允许时不要尝试。\n"
			+ "- 写代码/文档时像专业编码助手一样工作：先用 find_files/list_dir/grep 了解项目结构，再用 read_file "
			+ "查看文件内容，然后用 write_file/edit_file 修改，最后用 run_command 运行验证。\n"
			+ "- 写文件时优先使用相对路径（会保存到工作区目录）；执行命令前先评估是否安全。\n"
			+ "- 坐标：x = 东，y = 上，z = 南。20 tick = 1 秒。物品栏格 0-8。";

	public static McAiConfig defaults() {
		return new McAiConfig();
	}
}
