package types

// RagRequest POST /rag/ask 请求体
type RagRequest struct {
	KbId     int64  `json:"kbId" binding:"required"`
	Question string `json:"question" binding:"required"`
	TopK     int    `json:"topK"`
}

// SourceChunk 引用来源片段
type SourceChunk struct {
	ChunkId    int64   `json:"chunkId"`
	DocumentId int64   `json:"documentId"`
	FileName   string  `json:"fileName"`
	ChunkIndex int     `json:"chunkIndex"`
	Content    string  `json:"content"`
	Score      float64 `json:"score"`
}

// RagResponse 同步问答返回
type RagResponse struct {
	Answer    string        `json:"answer"`
	Sources   []SourceChunk `json:"sources"`
	LatencyMs int64         `json:"latencyMs"`
}
