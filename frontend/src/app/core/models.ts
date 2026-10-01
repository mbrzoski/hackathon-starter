// Mirrors the backend REST contracts (see /docs/API.md). Mock JSON in public/mock must match these shapes.

export interface Usage {
  inputTokens: number;
  outputTokens: number;
}

export interface Health {
  status: string;
  app: string;
  profiles: string[];
  time: string;
  database: string;
  llm: { provider: string; model: string; apiKeyConfigured: boolean };
}

export interface ChatRequest {
  system?: string;
  message: string;
  model?: string;
}

export interface ChatResponse {
  text: string;
  model: string;
  stopReason: string;
  usage: Usage;
}

export interface StructuredRequest {
  system?: string;
  prompt: string;
  schema: Record<string, unknown>;
}

export interface StructuredResponse {
  data: unknown;
}

export interface ToolChatRequest {
  system?: string;
  message: string;
  tools?: string[];
  maxIterations?: number;
}

export interface ToolStep {
  tool: string;
  input: unknown;
  output: string;
  error: boolean;
}

export interface ToolChatResult {
  text: string;
  steps: ToolStep[];
  usage: Usage;
  truncated: boolean;
}

export interface ToolDefinition {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details: string[];
}
