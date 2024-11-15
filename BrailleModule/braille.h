#define MAX_BRAILLE_NO 10

class braille
{
  public:
    uint8_t data[MAX_BRAILLE_NO]; //점자 모듈 저장 데이터: 최대 10개까지
    braille(int data_pin, int latch_pin, int clock_pin, int no);
    void begin();
    void on(int no, int index);
    void off(int no, int index);
    void refresh();
    void all_off();

  private:
    int braille_no;
    int dataPin;
    int latchPin;
    int clockPin;
};

//DATA, LATCH, CLOCK, 모듈 수 
braille::braille(int data_pin, int latch_pin, int clock_pin, int no)
{
  dataPin = data_pin;
  latchPin = latch_pin;
  clockPin = clock_pin;
  braille_no = no;
}

//출력 핀 설정 
void braille::begin()
{
  pinMode(dataPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
}

//점 위치 index 사용해 index 위치 비트 켜짐, 1로 설정 
void braille::on(int no, int index)
{
  data[braille_no - no + -1] = data[braille_no - no - 1] | ( 0b00000001 << index );
}

//index 위치 비트 1 반전시켜 0으로 설정 
void braille::off(int no, int index)
{
  data[braille_no - no + -1] = data[braille_no - no - 1] & ~( 0b00000001 << index );
}

//LATCH LOW 설정, 데이터 전송 후 LATCH HIGH 설정해 모듈 갱신 
void braille::refresh()
{
  digitalWrite(latchPin, LOW);
  for ( int i = 0; i < braille_no; i++)
  {
    shiftOut(dataPin, clockPin, LSBFIRST, data[i]);
  }
  digitalWrite(latchPin, HIGH);
}

//모듈 모든 점 끔 
void braille::all_off()
{
  for ( int i = 0; i < MAX_BRAILLE_NO; i++)
  {
    data[i] = 0b00000000;
  }
}
