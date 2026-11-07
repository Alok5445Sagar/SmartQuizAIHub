import React, { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { quizPublicAPI } from '../services/api';

const formatAttachmentSize = (bytes) => {
  const size = Number(bytes) || 0;
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

/**
 * Renders a chat attachment fetched with the Authorization header (no token in the URL).
 * Images show an inline preview; other files download on click.
 */
export default function ChatAttachment({ message, authToken, mine }) {
  const isImage = Boolean(message.attachmentContentType?.startsWith('image/'));
  const [previewUrl, setPreviewUrl] = useState('');

  useEffect(() => {
    if (!isImage || !authToken || !message.messageId) return undefined;
    let objectUrl = '';
    let cancelled = false;
    quizPublicAPI.fetchParticipantChatAttachment({ messageId: message.messageId, participantAuthToken: authToken })
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setPreviewUrl(objectUrl);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [isImage, authToken, message.messageId]);

  const download = async () => {
    try {
      const blob = await quizPublicAPI.fetchParticipantChatAttachment({
        messageId: message.messageId,
        participantAuthToken: authToken,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = message.attachmentName || 'attachment';
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(err?.response?.data?.detail || err?.response?.data?.message || 'Could not download attachment');
    }
  };

  if (isImage && previewUrl) {
    return (
      <button type="button" onClick={download} title={message.attachmentName || 'attachment'}
        style={{ display: 'inline-block', padding: 0, border: 'none', background: 'none', cursor: 'pointer' }}>
        <img
          src={previewUrl}
          alt={message.attachmentName || 'attachment'}
          style={{ maxWidth: 220, maxHeight: 180, borderRadius: 8, border: '1px solid rgba(148,163,184,0.45)' }}
        />
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={download}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        cursor: 'pointer',
        font: 'inherit',
        color: mine ? '#fff' : '#1d4ed8',
        background: mine ? 'rgba(255,255,255,0.18)' : '#eff6ff',
        borderRadius: 8,
        padding: '6px 8px',
        border: mine ? '1px solid rgba(255,255,255,0.32)' : '1px solid #bfdbfe',
      }}
    >
      <span>📎</span>
      <span style={{ fontSize: 12 }}>
        {message.attachmentName || 'Attachment'}
        {message.attachmentSizeBytes ? ` (${formatAttachmentSize(message.attachmentSizeBytes)})` : ''}
      </span>
    </button>
  );
}
