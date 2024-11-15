//점자 모듈
#include "braille.h"
int dataPin = 2; //DATA
int latchPin = 3; //LATCH
int clockPin = 4; //CLOCK
int no_module = 1; //모듈 수 
braille bra(dataPin, latchPin, clockPin, no_module);


char string_buffer[100]; //수신 받은 문자열
char string_buffer_serial[100][4]; //수신 받은 문자열 글자 단위 분리한 배열 
int str_char_count = 0; //전체 문자 수


//한글 초성 점자 표시 
byte hangul_cho[19] =
{
  0b00010000,//ㄱ
  0b00010000,//ㄲ
  0b00110000,//ㄴ
  0b00011000,//ㄷ
  0b00011000,//ㄸ
  0b00000100,//ㄹ
  0b00100100,//ㅁ
  0b00010100,//ㅂ
  0b00010100,//ㅃ
  0b00000001,//ㅅ
  0b00000001,//ㅆ
  0b00001111,//o
  0b00100010,//ㅈ
  0b00100010,//ㅉ
  0b00001010,//ㅊ
  0b00001110,//ㅋ
  0b00001011,//ㅌ
  0b00001101,//ㅍ
  0b00000111 //ㅎ
};
//초성 코드 번호
byte hangul2_cho[19] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};

//한글 중성 점자 표시 
byte hangul_jung[21] =
{
  0b00101001, // ㅏ
  0b00101110, // ㅐ
  0b00010110, // ㅑ
  0b00010110, // ㅒ
  0b00011010, // ㅓ
  0b00110110, // ㅔ
  0b00100101, // ㅕ
  0b00010010, // ㅖ
  0b00100011, // ㅗ
  0b00101011, // ㅘ
  0b00101011, // ㅙ
  0b00110111, // ㅚ
  0b00010011, // ㅛ
  0b00110010, // ㅜ
  0b00111010, // ㅝ
  0b00111010, // ㅞ
  0b00110010, // ㅟ
  0b00110001, // ㅠ
  0b00011001, // ㅡ
  0b00011101, // ㅢ
  0b00100110 // ㅣ
};
//중성 코드 번호 
byte hangul2_jung[21] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};

//한글 종성 점자 표시 
byte hangul_jong[28] =
{
  0b00000000, // 없음
  0b00100000, // ㄱ
  0b00100000, // ㄲ
  0b00100000, // ㄳ
  0b00001100, // ㄴ
  0b00001100, // ㄵ
  0b00001100, // ㄶ
  0b00000110, // ㄷ
  0b00001000, // ㄹ
  0b00001000, // ㄺ
  0b00001000, // ㄻ
  0b00001000, // ㄼ
  0b00001000, // ㄽ
  0b00001000, // ㄾ
  0b00001000, // ㄿ
  0b00001000, // ㅀ
  0b00001001, // ㅁ
  0b00101000, // ㅂ
  0b00101000, // ㅄ
  0b00000010, // ㅅ
  0b00000010, // ㅆ
  0b00001111, // ㅇ
  0b00100010, // ㅈ
  0b00001010, // ㅊ
  0b00001110, // ㅋ
  0b00001011, // ㅌ
  0b00001101, // ㅍ
  0b00000111 // ㅎ
};
//종성 코드 번호 
byte hangul2_jong[28] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27};


//시리얼 설정, 점자 모듈 초기화  
void setup()
{
  Serial.begin(9600);
  bra.begin();
  delay(1000);
  bra.all_off();
  bra.refresh();
  delay(100);
}


//문자 입력 
void loop()
{
  if (Serial.available()) //문자 입력
  {
    String str = Serial.readStringUntil('\n'); //문자열 끝까지 수신
    str.replace("\r", ""); //문자 종료 \r을 제거
    strcpy(string_buffer, str.c_str()); //정리된 문자열 string_buffer 저장

    //입력 받은 글자 각 배열 할당
    int ind = 0; //처리중인 문자열 BYTE 위치
    int len = strlen(string_buffer); //문자열 BYTE 수
    int index = 0; //문자의 현재 처리 수
    
    while (ind < len) //처리 완료 시까지
    {
      int bytes = get_char_byte(string_buffer + ind); //해당 위치 첫번째 바이트 읽음
      if (bytes == 3)
      {
        string_buffer_serial[index][0] = *(string_buffer + ind);
        string_buffer_serial[index][1] = *(string_buffer + ind + 1);
        string_buffer_serial[index][2] = *(string_buffer + ind + 2);
        string_buffer_serial[index][3] = 0;
        index++;
      }
      ind += bytes;
    }
    str_char_count = index;

    for (int i = 0; i < str_char_count; i++) //전체 문자 대해 처리
    {
      unsigned int cho, jung, jong;
      split_han_cho_jung_jong(string_buffer_serial[i][0], string_buffer_serial[i][1], string_buffer_serial[i][2], cho, jung, jong);
      han_braille(cho, jung, jong);
      delay(500);
      bra.all_off();
      bra.refresh();
      delay(500);

      Serial.print("점자 출력 - 한글 : ");
      Serial.print(hangul_cho[cho], BIN);
      Serial.print(",");
      Serial.print(hangul_jung[jung], BIN);
      Serial.print(",");
      Serial.print(hangul_jong[jong], BIN);
      Serial.print("\n");
    }
    Serial.println("");
  }
}



//각 글자 바이트 수 확인 함수 
unsigned char get_char_byte(char *pos)
{
  char val = *pos;
  if ( ( val & 0b10000000 ) == 0 )
  {
    return 1;
  }
  else if ( ( val & 0b11100000 ) == 0b11000000 )
  {
    return 2;
  }
  else if ( ( val & 0b11110000 ) == 0b11100000 )
  {
    return 3;
  }
  else if ( ( val & 0b11111000 ) == 0b11110000 )
  {
    return 4;
  }
  else if ( ( val & 0b11111100 ) == 0b11111000 )
  {
    return 5;
  }
  else
  {
    return 6;
  }
}




//한글 초성, 중성, 종성 분리 함수 
void split_han_cho_jung_jong(char byte1, char byte2, char byte3, unsigned int &cho, unsigned int &jung, unsigned int &jong)
{
  unsigned int utf16 = (byte1 & 0b00001111) << 12 | (byte2 & 0b00111111) << 6 | (byte3 & 0b00111111);

  unsigned int val = utf16 - 0xac00;

  unsigned char _jong = val % 28;
  unsigned char _jung = (val % (28 * 21)) / 28;
  unsigned char _cho =  val / (28 * 21);

  cho = 0;
  for ( int i = 0; i < 19; i++)
  {
    if ( _cho == hangul2_cho[i] )
    {
      cho = i;
    }
  }

  jung = 0;
  for ( int i = 0; i < 21; i++)
  {
    if ( _jung == hangul2_jung[i] )
    {
      jung = i;
    }
  }

  jong = 0;
  for ( int i = 0; i < 28; i++)
  {
    if ( _jong == hangul2_jong[i] )
    {
      jong = i;
    }
  }
}



//점자 모듈 한글 초성 중성 종성 출력 함수 
void han_braille(int index1, int index2, int index3)
{
  bool isTenseSound = (index1 == 1 || index1 == 4 || index1 == 8 || index1 == 10 || index1 == 13);

  if (isTenseSound)
  {
    bra.all_off();
    display_doen(0b00000001); //된소리 표기 점자 출력
    delay(400);
  }

  //초성 출력 
  bra.all_off();
  for (int i = 0; i < 6; i++)
  {
    int on_off = hangul_cho[index1] >> (5 - i) & 0b00000001;
    if (on_off != 0)
    {
      bra.on(0, i);
    }
    else
    {
      bra.off(0, i);
    }
  }
  bra.refresh();
  delay(500);

  //중성 출력 
  bra.all_off();
  if (index2 == 16) // 'ㅟ'
  {
    display_han_jung(0b00110010);
    display_han_jung(0b00101110);
  }
  else if (index2 == 3) // 'ㅒ'
  {
    display_han_jung(0b00010110);
    display_han_jung(0b00101110);
  }
  else if (index2 == 10) // 'ㅙ'
  {
    display_han_jung(0b00101011);
    display_han_jung(0b00101110);
  }
  else if (index2 == 15) // 'ㅞ'
  {
    display_han_jung(0b00111010);
    display_han_jung(0b00101110);
  }
  else
  {
    for (int i = 0; i < 6; i++)
    {
      int on_off = hangul_jung[index2] >> (5 - i) & 0b00000001;
      if (on_off != 0)
      {
        bra.on(0, i);
      }
      else
      {
        bra.off(0, i);
      }
    }
    bra.refresh();
    delay(500);
  }


  //종성 출력 
  if (index3 != 0)
  {
    int first_jong, second_jong = -1;
    switch (index3)
    {
      case 3:  first_jong = 1; second_jong = 9; break;
      case 5:  first_jong = 4; second_jong = 12; break;
      case 6:  first_jong = 4; second_jong = 18; break;
      case 9:  first_jong = 8; second_jong = 1; break;
      case 10: first_jong = 8; second_jong = 6; break;
      case 11: first_jong = 8; second_jong = 7; break;
      case 12: first_jong = 8; second_jong = 9; break;
      case 13: first_jong = 8; second_jong = 15; break;
      case 14: first_jong = 8; second_jong = 16; break;
      case 15: first_jong = 8; second_jong = 18; break;
      case 18: first_jong = 17; second_jong = 19; break;
      default: first_jong = index3;
    }

    bra.all_off();
    for (int i = 0; i < 6; i++)
    {
      int on_off = hangul_jong[first_jong] >> (5 - i) & 0b00000001;
      if (on_off != 0)
      {
        bra.on(0, i);
      }
      else
      {
        bra.off(0, i);
      }
    }
    bra.refresh();
    delay(500);

    if (second_jong != -1)
    {
      bra.all_off();
      for (int i = 0; i < 6; i++)
      {
        int on_off = hangul_jong[second_jong] >> (5 - i) & 0b00000001;
        if (on_off != 0)
        {
          bra.on(0, i);
        }
        else
        {
          bra.off(0, i);
        }
      }
      bra.refresh();
      delay(500);
    }
  }
}



//한글 중성 함수 
void display_han_jung(byte pattern)
{
  bra.all_off();
  for (int i = 0; i < 6; i++)
  {
    int on_off = pattern >> (5 - i) & 0b00000001;
    if (on_off != 0)
    {
      bra.on(0, i);
    }
    else
    {
      bra.off(0, i);
    }
  }
  bra.refresh();
  delay(500);
}


//한글 된소리 함수
void display_doen(byte pattern)
{
  bra.all_off();
  for (int i = 0; i < 6; i++)
  {
    int on_off = pattern >> (5 - i) & 0b00000001;
    if (on_off != 0)
    {
      bra.on(0, i);
    }
    else
    {
      bra.off(0, i);
    }
  }
  bra.refresh();
  delay(100);
}
