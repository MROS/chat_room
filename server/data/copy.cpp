#include <stdio.h>
#include <string.h>
#include <iostream>
using namespace std;

struct Student {
	char name[100];
	int number;
} /* optional variable list */;

int main(int argc, const char *argv[]){
	Student yuja;
	strcpy(yuja.name, "yuja");
	yuja.number = 945415;

	Student copy;
	copy = yuja;

	cout << "original: " << yuja.name << " " << yuja.number << endl;
	printf("%p\n", yuja.name);
	cout << "copy: " << copy.name << " " << copy.number << endl;
	printf("%p\n", copy.name);
	return 0;
}
