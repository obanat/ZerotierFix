
#include <Arduino.h>


// 电机控制引脚定义
const int motor1_A = 12;   // 电机1脉冲信号
const int motor1_B = 14;    // 电机1方向信号
const int motor2_A = 26;  // 电机2脉冲信号  
const int motor2_B = 27;     // 电机2方向信号


// 电机速度控制参数
const int pulse_delay = 10;  // 脉冲高低电平持续时间(微秒)，影响转速
const unsigned long MOVE_DURATION = 300;  // 电机运行持续时间(毫秒)


// 串口输入
String inputString = "";     // 存储接收的串口数据
int inputStringLen = 0;
bool stringComplete = false; // 标志是否收到完整字符串

// 电机状态控制
enum MotorState {
  STOP,
  FORWARD,
  BACKWARD,
  LEFT,
  RIGHT
};

MotorState currentState = STOP;
unsigned long stateStartTime = 0;  // 状态开始时间
bool motorRunning = false;           // 电机是否正在运行脉冲
unsigned long lastPulseTime = 0;     // 上次脉冲切换时间


void setup() {
  // 初始化串口通信
  Serial.begin(115200);
  
  // 配置电机控制引脚
  pinMode(motor1_A, OUTPUT);
  pinMode(motor1_B, OUTPUT);
  pinMode(motor2_A, OUTPUT);
  pinMode(motor2_B, OUTPUT);
  
  // 初始状态停止电机
  stopMotors();
  
  Serial.println("ESP32 Motor Control Ready");
  Serial.println("Commands: MO11(forward), MO22(backward), MO00(stop), MO10(left), MO01(right)");
}

void loop() {
  // 处理串口/UDP命令
  processCommand();
  
  // 非阻塞式电机控制
  controlMotors();
}

// 处理串口命令
void processCommand() {
  if (Serial.available()) {
    char inChar = (char)Serial.read();
    
    if (inChar == '\n' || inChar == '\r') {
      stringComplete = true;
    } else if (inputStringLen >= 10) {
      return;
    } else {
      inputString += inChar;
      inputStringLen += 1;
      return;
    }
  }

  if (stringComplete && inputStringLen > 0) {
    inputString.trim();  // 去除首尾空白字符
    
    Serial.println("receive Command:" + inputString);
    
    // 根据指令控制电机运动
    if (inputString == "MO11") {
      startMotor(FORWARD);
    } else if (inputString == "MO22") {
      startMotor(BACKWARD);
    } else if (inputString == "MO00") {
      startMotor(STOP);
    } else if (inputString == "MO10") {
      startMotor(LEFT);
    } else if (inputString == "MO01") {
      startMotor(RIGHT);
    }
    
    // 清空接收缓冲区
    inputString = "";
    stringComplete = false;
    inputStringLen = 0;
  }
}

// 启动电机
void startMotor(MotorState newState) {
  currentState = newState;
  stateStartTime = millis();
  motorRunning = false;  // 重置脉冲状态，让controlMotors立即开始脉冲
  lastPulseTime = micros();  // 使用micros()获得更精确的时间控制
  
  Serial.print("Motor started: ");
  Serial.println(newState == FORWARD ? "FORWARD" : 
                 newState == BACKWARD ? "BACKWARD" : 
                 newState == LEFT ? "LEFT" : 
                 newState == RIGHT ? "RIGHT" : "STOP");
}

// 非阻塞式电机控制
void controlMotors() {
  unsigned long currentTime = millis();
  
  // 检查是否超过运行时间（STOP命令立即生效，不检查时间）
  if (currentState != STOP) {
    if (currentTime - stateStartTime >= MOVE_DURATION) {
      // 时间到了，停止电机
      stopMotors();
      currentState = STOP;
      return;
    }
  }
  
  // 使用micros()进行高精度脉冲控制
  unsigned long currentMicros = micros();
  
  // 脉冲切换阈值
  unsigned long pulseInterval = pulse_delay * 1000;  // 转换为微秒
  
  if (currentMicros - lastPulseTime >= pulseInterval) {
    motorRunning = !motorRunning;  // 切换脉冲状态
    
    // 根据当前状态设置电机输出
    switch (currentState) {
      case FORWARD:
        digitalWrite(motor1_A, motorRunning ? HIGH : LOW);
        digitalWrite(motor1_B, LOW);
        digitalWrite(motor2_A, motorRunning ? HIGH : LOW);
        digitalWrite(motor2_B, LOW);
        break;
        
      case BACKWARD:
        digitalWrite(motor1_A, LOW);
        digitalWrite(motor1_B, motorRunning ? HIGH : LOW);
        digitalWrite(motor2_A, LOW);
        digitalWrite(motor2_B, motorRunning ? HIGH : LOW);
        break;
        
      case LEFT:
        // 左转：左侧电机反转，右侧电机正转
        digitalWrite(motor1_A, motorRunning ? HIGH : LOW);
        digitalWrite(motor1_B, LOW);
        digitalWrite(motor2_A, LOW);
        digitalWrite(motor2_B, motorRunning ? HIGH : LOW);
        break;
        
      case RIGHT:
        // 右转：左侧电机正转，右侧电机反转
        digitalWrite(motor1_A, LOW);
        digitalWrite(motor1_B, motorRunning ? HIGH : LOW);
        digitalWrite(motor2_A, motorRunning ? HIGH : LOW);
        digitalWrite(motor2_B, LOW);
        break;
        
      case STOP:
      default:
        stopMotors();
        break;
    }
    
    lastPulseTime = currentMicros;
  }
}

// 停止函数 - 停止两个电机
void stopMotors() {
  digitalWrite(motor1_A, LOW);
  digitalWrite(motor1_B, LOW);
  digitalWrite(motor2_A, LOW);
  digitalWrite(motor2_B, LOW);
  motorRunning = false;
}
