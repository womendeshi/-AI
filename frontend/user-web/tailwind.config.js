/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 深色主题色系 - 参考「字字动画」设计
        // 背景层级
        'bg-base': '#1a1a1a',         // 主背景（深黑）
        'bg-elevated': '#232323',     // 卡片/浮层背景（深灰）
        'bg-subtle': '#2a2a2a',       // 次级背景
        'bg-hover': '#333333',        // hover 背景
        'bg-sidebar': '#1e1e1e',      // 侧边栏背景
        
        // 文字层级
        'text-primary': '#ffffff',    // 主要文字（白色）
        'text-secondary': '#b3b3b3',  // 次要文字（浅灰）
        'text-tertiary': '#808080',   // 辅助文字（中灰）
        'text-disabled': '#4d4d4d',   // 禁用文字（暗灰）
        
        // 边框层级
        'border-subtle': '#2a2a2a',   // 极浅边框
        'border-default': '#333333',  // 默认边框
        'border-strong': '#404040',   // 强调边框
        
        // 强调色（蓝紫色渐变）
        'accent': '#8B5CF6',          // 主强调色（紫色）
        'accent-light': '#A78BFA',    // 浅强调色
        'accent-dark': '#7C3AED',     // 深强调色
        'accent-blue': '#3B82F6',     // 蓝色（用于渐变）
        
        // 功能色
        'success': '#52c41a',
        'warning': '#faad14',
        'error': '#ff4d4f',
        'info': '#1890ff',
        
        // 灰度色阶（深色主题）
        'gray-50': '#2a2a2a',
        'gray-100': '#333333',
        'gray-200': '#404040',
        'gray-300': '#4d4d4d',
        'gray-400': '#666666',
        'gray-500': '#808080',
        'gray-600': '#999999',
        'gray-700': '#b3b3b3',
        'gray-800': '#cccccc',
        'gray-900': '#e6e6e6',
      },
      boxShadow: {
        'xs': '0 1px 2px 0 rgba(0, 0, 0, 0.3)',
        'sm': '0 1px 3px 0 rgba(0, 0, 0, 0.4), 0 1px 2px 0 rgba(0, 0, 0, 0.3)',
        'DEFAULT': '0 2px 4px 0 rgba(0, 0, 0, 0.5), 0 2px 4px 0 rgba(0, 0, 0, 0.4)',
        'md': '0 4px 8px 0 rgba(0, 0, 0, 0.5), 0 2px 4px 0 rgba(0, 0, 0, 0.4)',
        'lg': '0 8px 16px 0 rgba(0, 0, 0, 0.6), 0 4px 8px 0 rgba(0, 0, 0, 0.5)',
        'xl': '0 12px 24px 0 rgba(0, 0, 0, 0.7), 0 8px 16px 0 rgba(0, 0, 0, 0.6)',
        'glow': '0 0 20px rgba(139, 92, 246, 0.3)',
      },
      spacing: {
        '18': '4.5rem',
        '88': '22rem',
        '128': '32rem',
      },
      borderRadius: {
        'DEFAULT': '6px',
        'md': '8px',
        'lg': '12px',
        'xl': '16px',
      },
      fontSize: {
        'xs': ['12px', { lineHeight: '16px', letterSpacing: '0.01em' }],
        'sm': ['14px', { lineHeight: '20px', letterSpacing: '0.01em' }],
        'base': ['16px', { lineHeight: '24px', letterSpacing: '0' }],
        'lg': ['18px', { lineHeight: '28px', letterSpacing: '-0.01em' }],
        'xl': ['20px', { lineHeight: '28px', letterSpacing: '-0.01em' }],
        '2xl': ['24px', { lineHeight: '32px', letterSpacing: '-0.02em' }],
        '3xl': ['30px', { lineHeight: '38px', letterSpacing: '-0.02em' }],
      },
      fontFamily: {
        'sans': ['-apple-system', 'BlinkMacSystemFont', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
