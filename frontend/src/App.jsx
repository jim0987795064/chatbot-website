import { useEffect, useRef, useState } from "react";

const API_URL =
    import.meta.env.VITE_API_URL ??
    "http://localhost:8080/api/chat";

const GENERIC_ERROR = "發生錯誤，請稍後再試。";

function App() {
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const bottomRef = useRef(null);

  async function loadHistory() {
    try {
      const response = await fetch(
          `${API_URL}/history`
      );

      if (!response.ok) {
        throw new Error(
            `HTTP error: ${response.status}`
        );
      }

      const data = await response.json();

      /*
       * 後端若回了非陣列（例如錯誤物件），直接 setMessages
       * 會讓底下的 .map 整頁炸掉，所以先驗型別。
       */
      if (!Array.isArray(data)) {
        throw new Error(
            "Unexpected history payload."
        );
      }

      setMessages(data);
      setError(null);
    } catch (caught) {
      console.error(caught);
      setError("載入歷史紀錄失敗。");
    }
  }

  async function sendMessage() {
    if (!message.trim() || loading) {
      return;
    }

    const text = message;

    setMessage("");
    setError(null);

    setMessages((previous) => [
      ...previous,
      {
        role: "user",
        content: text,
      },
    ]);

    setLoading(true);

    try {
      const response = await fetch(API_URL, {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
        },

        body: JSON.stringify({
          message: text,
        }),
      });

      if (!response.ok) {
        throw new Error(
            `HTTP error: ${response.status}`
        );
      }

      const data = await response.json();

      setMessages((previous) => [
        ...previous,
        {
          role: "assistant",
          content: data.answer,
        },
      ]);
    } catch (caught) {
      console.error(caught);

      setMessages((previous) => [
        ...previous,
        {
          role: "assistant",
          content: GENERIC_ERROR,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  async function clearHistory() {
    try {
      const response = await fetch(`${API_URL}/history`, {
        method: "DELETE",
      });

      if (!response.ok) {
        throw new Error(
            `HTTP error: ${response.status}`
        );
      }

      /*
       * 只有後端確實刪掉才清畫面，否則 UI 會顯示空白
       * 但資料還在資料庫裡。
       */
      setMessages([]);
      setError(null);
    } catch (caught) {
      console.error(caught);
      setError("清除紀錄失敗，資料仍保留在伺服器上。");
    }
  }

  function handleKeyDown(event) {
    if (
        event.key === "Enter" &&
        !event.shiftKey
    ) {
      event.preventDefault();
      sendMessage();
    }
  }

  /*
   * 掛載時從後端載入歷史。loadHistory 的 setState 都發生在 await
   * 之後，不是同步觸發 re-render，linter 看不穿 async function。
   */
  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    loadHistory();
  }, []);

  // 新訊息進來就捲到底
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  return (
      <div className="app">
        <div className="chat-container">
          <div className="header">
            <div>
              <h1>GPT Chat</h1>
              <p>
                React + Spring Boot +
                MySQL + GPT
              </p>
            </div>

            <button
                className="clear-button"
                onClick={clearHistory}
            >
              清除紀錄
            </button>
          </div>

          {error && (
              <div className="error-banner" role="alert">
                {error}
              </div>
          )}

          <div className="messages">
            {messages.length === 0 && (
                <div className="empty">
                  開始跟 GPT 聊天吧
                </div>
            )}

            {messages.map(
                (item, index) => (
                    <div
                        key={item.id ?? index}
                        className={
                          item.role === "user"
                              ? "message user"
                              : "message assistant"
                        }
                    >
                      <div className="role">
                        {item.role === "user"
                            ? "You"
                            : "GPT"}
                      </div>

                      <div className="content">
                        {item.content}
                      </div>
                    </div>
                )
            )}

            {loading && (
                <div className="message assistant">
                  <div className="role">
                    GPT
                  </div>

                  <div className="content">
                    Thinking...
                  </div>
                </div>
            )}

            <div ref={bottomRef} />
          </div>

          <div className="input-area">
                    <textarea
                        value={message}
                        placeholder="輸入訊息..."
                        onChange={(event) =>
                            setMessage(
                                event.target.value
                            )
                        }
                        onKeyDown={handleKeyDown}
                    />

            <button
                onClick={sendMessage}
                disabled={loading}
            >
              Send
            </button>
          </div>
        </div>
      </div>
  );
}

export default App;
