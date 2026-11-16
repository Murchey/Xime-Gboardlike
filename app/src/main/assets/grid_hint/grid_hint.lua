--[[ 合并键方案的候选拼音提示

rime 的 comment 走 GetOriginalSpelling，对 algebra derive 出来的组码返回空，
所以这里自己算：候选文字 + 当前输入（组码）+ 组码映射 + 单字读音表 → 拼音。

读音表走 require 加载 lua/grid_pinyin.lua（librime-lua 没开 io，不能读外部文件）。
任何异常都不许打断翻译：最外层 pcall，出错就原样放行候选。
]]

local M = {}

local pinyin = nil

local function utf8_chars(s)
    local out, i = {}, 1
    while i <= #s do
        local c = s:byte(i) or 0
        local len = 1
        if c >= 0xF0 then len = 4
        elseif c >= 0xE0 then len = 3
        elseif c >= 0xC0 then len = 2 end
        out[#out + 1] = s:sub(i, i + len - 1)
        i = i + len
    end
    return out
end

local function encode(syllable, map)
    local out = {}
    for i = 1, #syllable do
        local g = map[syllable:sub(i, i)]
        if not g then return nil end
        out[#out + 1] = g
    end
    return table.concat(out)
end

local function align(chars, code, map)
    local function step(i, pos, acc)
        if i > #chars then return table.concat(acc, ' ') end
        local list = pinyin[chars[i]]
        if not list then return nil end
        for _, reading in ipairs(list) do
            local enc = encode(reading, map)
            if enc and pos + #enc - 1 <= #code and code:sub(pos, pos + #enc - 1) == enc then
                acc[#acc + 1] = reading
                local ok = step(i + 1, pos + #enc, acc)
                if ok then return ok end
                acc[#acc] = nil
            end
        end
        return nil
    end
    return step(1, 1, {})
end

function M.init(env)
    env.name_space = env.name_space:gsub('^*', '')
    local config = env.engine.schema.config
    M.map = {}
    local mapstr = config:get_string(env.name_space .. '/group_map')
    if mapstr and #mapstr == 26 then
        for i = 1, 26 do
            M.map[string.char(96 + i)] = mapstr:sub(i, i)
        end
    end
    M.require_ok = false
    pcall(function()
        local data = require('grid_pinyin')
        if type(data) == 'table' and next(data) then
            pinyin = data
            M.require_ok = true
        end
    end)
end

function M.func(input, env)
    local code = ''
    pcall(function() code = env.engine.context.input:gsub('%s', '') end)
    for cand in input:iter() do
        local ok, py = pcall(function()
            if not M.map or #code == 0 or not pinyin then return nil end
            local chars = utf8_chars(cand.text)
            if #chars == 0 or #chars > 8 then return nil end
            return align(chars, code, M.map)
        end)
        if ok and py then cand.comment = py end
        yield(cand)
    end
end

return M